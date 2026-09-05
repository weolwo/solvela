package solvela.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.app.domain.ActivityView;
import solvela.app.domain.DrawRequest;
import solvela.app.domain.DrawView;
import solvela.app.domain.DrawView.DrawItemView;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.ActivityDrawCmd;
import solvela.marketing.api.ActivityRuleView;
import solvela.marketing.api.DrawRejectReason;
import solvela.marketing.api.DrawResultView;

import java.util.List;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import solvela.app.domain.PromoView;

/**
 * 活动的接入层：<b>翻译 + 组装</b>，没有业务逻辑。
 *
 * <p>活动是否可参与、抽哪个奖池、次数够不够，全部在营销服务里判 ——
 * 本类一行都不判。它只做三件网关该做的事：
 * <ol>
 *   <li>把域给的 {@link DrawRejectReason} 翻成 HTTP 状态码与<b>给用户看的话</b>；</li>
 *   <li>把服务间的 view 组装成 C 端的形状（内部字段不下发）；</li>
 *   <li>「能不能参与」用<b>服务端时钟</b>算好再下发，不让客户端自己判。</li>
 * </ol>
 *
 * <h3>措辞在这一层，不在域里</h3>
 * 同一个 {@code POOL_CLOSED}，C 端要说「活动暂时无法参与」，而内部工具要看到原因本身。
 * 域只给 reason，说什么由这里决定 —— 与登录那条链路是同一套分工。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityApi activityApi;

    /**
     * 活动详情。活动不存在 → 404。
     */
    public ActivityView getActivity(String activityCode) {
        ActivityRuleView view = activityApi.getActivityRule(activityCode);
        if (view == null) {
            throw new ApiException(ApiErrors.NOT_FOUND, "活动不存在或已结束");
        }
        LocalDateTime now = LocalDateTime.now();
        return new ActivityView(
                view.activityCode(), view.activityName(), view.activityType(), view.status(),
                view.startTime(), view.endTime(),
                view.subTitle(), view.themeColor(),
                view.mainImageId(), view.bgImageId(), view.shareImageId(),
                view.shareTitle(), view.shareDesc(), view.extraConfig(), view.ruleContent(),
                // 用服务端时钟算：客户端自己判等于把判据抄一份到前端，而它算的是客户端的时钟
                view.joinable(now), view.claimable(now));
    }

    /**
     * C 端可见的活动列表。首页焦点位与活动中心用同一个接口 ——
     * 「精选前三条」是<b>页面</b>的取舍，不是两个接口。
     *
     * <p>没有活动时返回空列表，不是 404：新环境、活动都下线了，都是正常状态。
     *
     * <p>顺序<b>照域给的原样透出</b>，网关不重排 —— 重排一次就是第二份排序规则。
     */
    public List<PromoView> listOpen() {
        LocalDateTime now = LocalDateTime.now();
        return activityApi.listOpenActivities().stream()
                .map(brief -> new PromoView(
                        brief.activityCode(),
                        brief.activityName(),
                        brief.subTitle(),
                        brief.themeColor(),
                        format(brief.startTime()),
                        format(brief.endTime()),
                        brief.mainImageId(),
                        // 用服务端时钟算：客户端自己判等于把判据抄一份到前端，而它算的是客户端的时钟
                        brief.joinable(now)))
                .toList();
    }

    /**
     * 时间统一按 {@code yyyy-MM-dd HH:mm:ss} 的字符串下发，<b>不带时区</b> ——
     * 全站契约就是这个（见前端 types/contract 的 DateTimeString）。
     * 这里显式格式化而不是让 Jackson 去猜，是因为「猜」的结果取决于全局配置，
     * 改一次全局配置就会把所有接口的时间格式一起改掉。
     */
    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }

    /**
     * 抽一次或连抽。
     *
     * <p>「没被受理」一律翻成 4xx —— 它们全是<b>预期内</b>的情况，
     * 不该让用户看到「服务开小差了」，也不该让监控上多出一堆假的服务端错误。
     *
     * <p>⚠️ {@code times} 只是客户端的<b>意愿</b>，真正抽了几次看 {@code result.times()} ——
     * 编排脚本可能按剩余次数给了别的数。所以下发的是<b>实际记录</b>，
     * 不是把请求里那个数回显给前端。
     */
    public DrawView draw(String activityCode, Long memberId, DrawRequest request) {
        DrawResultView result = activityApi.draw(new ActivityDrawCmd(
                activityCode, memberId, request.requestId(), request.timesOrOne(), request.params()));

        if (!result.accepted()) {
            throw translate(activityCode, result.reject());
        }
        // prizeItemId 与 source 刻意不下发，见 DrawView 的类注释
        List<DrawItemView> records = result.records().stream()
                .map(record -> new DrawItemView(record.hit(), record.prizeCode()))
                .toList();
        return new DrawView(records, result.hitCount(), summary(result));
    }

    /**
     * 给用户看的那句话。
     *
     * <h3>文案在这一层，是这条链路上唯一合适的位置</h3>
     * 域侧的 {@code DrawResultView} 已经不带 message 了 —— 同一个「没中奖」，
     * C 端要说「手慢了」，内部联调工具要看到的是事实本身。
     * 域只陈述发生了什么，说什么由这里决定。
     */
    private static String summary(DrawResultView result) {
        long hits = result.hitCount();
        if (hits == 0) {
            return "手慢了，奖品已被抽完";
        }
        // 单抽不说「中了 1 个」—— 那句话只有在连抽时才是信息
        return result.times() == 1 ? "恭喜中奖" : "恭喜，中了 " + hits + " 个奖";
    }

    /**
     * 未受理原因 → HTTP 契约。
     *
     * <p>用 switch 表达式：营销侧新增一个 {@code DrawRejectReason} 时<b>这里编译不过</b>，
     * 而不是悄悄落进兜底分支返回一句「服务开小差了」。跨服务之后这一点更重要 ——
     * 两边是分开发版的，编译期能拦住的东西不该留到运行期。
     *
     * <h3>奖池那几种为什么都说同一句话</h3>
     * 「奖池不存在」「奖池未开启」「奖池没配奖项」「奖池配置坏了」对用户是同一件事：
     * <b>现在抽不了</b>。把区别告诉他既没用，又暴露了配置结构。
     * 真正的原因在营销服务的日志里，运维看得到。
     */
    private ApiException translate(String activityCode, DrawRejectReason reason) {
        return switch (reason) {
            case ACTIVITY_NOT_FOUND -> new ApiException(ApiErrors.NOT_FOUND, "活动不存在或已结束");
            case ACTIVITY_NOT_OPEN -> new ApiException(ApiErrors.CONFLICT, "活动不在进行中");
            case DUPLICATE_REQUEST -> new ApiException(ApiErrors.CONFLICT, "请勿重复提交");
            case TOO_FREQUENT -> new ApiException(ApiErrors.OPERATION_LIMITED, "手速太快了，稍后再试");
            case NO_PLAY_CONFIG -> {
                // 「玩法配置都没建」比「没挂脚本」更早一步，但对用户是同一句话。
                // 分开打日志是为了让运维一眼看出该去建配置还是该去挂脚本
                log.error("【活动配置缺失】活动 {} 的玩法配置没建（或被关闭），用户点了但玩不了", activityCode);
                yield new ApiException(ApiErrors.CONFLICT, "活动暂未开放，请稍后再试");
            }
            case NO_PLAY_SCRIPT -> {
                // 运营配置没做完。对用户含糊其辞是对的（说「没配脚本」他也不知道该干什么），
                // 但这一行日志必须有 —— 否则这个活动会安静地一个奖都发不出去
                log.error("【活动配置缺失】活动 {} 没挂玩法编排脚本，用户点了但抽不了", activityCode);
                yield new ApiException(ApiErrors.CONFLICT, "活动暂未开放，请稍后再试");
            }
            case INVALID_TIMES -> {
                // 走到这里说明【我们自己】把次数算错了（times <= 0）——
                // 用户没有任何办法让它发生，所以对用户是「服务出问题了」，不是「你填错了」。
                // 这一行日志必须有：它指向的是一个代码或脚本的 bug，不是运营配置问题
                log.error("【抽奖次数非法】活动 {} 请求了 <= 0 次抽奖，调用方把次数算错了", activityCode);
                yield new ApiException(ApiErrors.INTERNAL, "活动暂时无法参与，请稍后再试");
            }
            case POOL_NOT_FOUND, POOL_CLOSED, POOL_NO_PRIZE, POOL_BROKEN -> {
                log.warn("【奖池不可用】活动 {} 抽奖被拒，原因: {}", activityCode, reason);
                yield new ApiException(ApiErrors.CONFLICT, "活动暂时无法参与，请稍后再试");
            }
        };
    }
}
