package solvela.ledger.coupon.manual;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import solvela.coupon.CouponTemplate;
import solvela.enums.NotificationTemplateEnum;
import solvela.exception.BusinessException;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.issue.CouponIssueCmd;
import solvela.ledger.coupon.issue.CouponIssueService;
import solvela.ledger.coupon.manual.domain.ManualCouponGrantCmd;
import solvela.ledger.coupon.manual.domain.ManualCouponGrantResult;
import solvela.ledger.coupon.template.service.CouponTemplateService;
import solvela.member.service.MemberService;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 人工发券：客服 / 运营给指定会员补一张券。
 *
 * <h3>🔴 收件人有硬上限，超了直接拒绝</h3>
 * 和人工发站内信同一条红线，而且这里更重 —— <b>它直接对应钱</b>。
 * 不设上限的话，这个入口会变成「给全体用户发券」的后门：绕过活动配置、
 * 绕过预算、绕过风控，而且事后只在券表里留下一堆看不出批次的行。
 *
 * <p>每人张数也有上限：「每人 1000 张」和「发给 1000 个人」是同一件事的两种写法，
 * 只拦一个等于没拦。
 *
 * <h3>🔴 没有券模板就<b>拒绝</b>，不降级</h3>
 * 这和自动发券那条路（{@code CouponIssueService} 找不到模板时照发）<b>刚好相反</b>，
 * 而两个判断都是对的，因为处境不同：
 *
 * <ul>
 *   <li><b>自动发券</b>拒发，会在运行期把一个在架商品变成兑换必失败 ——
 *       用户什么都没做错却拿不到东西，代价完全不对等；</li>
 *   <li><b>人工发券</b>是一个人在后台<b>从列表里选</b>券模。选到一个没有模板的编码，
 *       只可能是选错了。这时候发出去一张没有规则的券，等于用一次「看起来成功」
 *       换一张用户永远用不了的券，而他还会为此再来一次客服。</li>
 * </ul>
 *
 * <h3>幂等靠数据库，不靠先查一遍</h3>
 * {@code uk_source(source_type, source_biz_id)}。最真实的故障就是运营双击、
 * 或者网络慢了再点一次 —— 先查再插在并发下挡不住，条件交给数据库才挡得住。
 *
 * <p>幂等键是 {@code <工单号>:<会员号>:<序号>}：会员号<b>必须</b>在里面，
 * 否则一个工单发给 5 个人会在第二个人身上撞键，表现是「只有第一个人收到了」。
 *
 * <p>⚠️ 2026-09-15 之前这里是一个<b>只对 MANUAL 生效</b>的函数索引
 *（{@code uk_manual_src}），因为当时库里有 53 组 PROPOSAL 重复、建不出全表唯一键。
 * 那 53 组清掉之后换成了普通唯一键，发奖那条路也跟着被管起来了。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponManualGrantService {

    /**
     * 单次收件人硬上限。
     *
     * <p>⚠️ 与人工发站内信的 200 刻意<b>取同一个量级但各存一份</b>：
     * 那边的约束来自「写扩散会把库撑爆」，这边来自「发券就是发钱」。
     * 哪天要调，两边不该被迫一起调。
     */
    private static final int MAX_RECIPIENTS = 200;

    /** 每人最多几张。不拦的话「每人 1000 张」和「发给 1000 个人」是一回事 */
    private static final int MAX_QUANTITY_PER_MEMBER = 10;

    private static final String SOURCE_TYPE_MANUAL = "MANUAL";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CouponTemplateService couponTemplateService;
    private final CouponIssueService couponIssueService;
    private final MemberCouponDao memberCouponDao;
    private final MemberService memberService;
    private final NotificationService notificationService;

    /**
     * 发券。
     *
     * <h3>⚠️ 刻意<b>没有</b> {@code @Transactional}</h3>
     * 一人一行，各自独立。整批包在一个事务里的话，第 199 个人失败会把前面 198 个
     * 已经发出去的券一起回滚 —— 而那 198 个人已经收到通知了。
     *
     * <p>失败的那几个由返回值报出来，运营对着名单重发一次即可（幂等键挡着，
     * 已经成功的不会重复）。
     */
    public ManualCouponGrantResult grant(ManualCouponGrantCmd cmd, String operator) {
        List<Long> memberIds = cmd.memberIds() == null ? List.of() : cmd.memberIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        validate(cmd, memberIds, operator);

        CouponTemplate template = couponTemplateService.getLatestEnabled(cmd.couponCode());
        if (template == null) {
            // 人工发券选错编码是最可能的情况，直接把失败提前到点「发送」那一刻
            throw new BusinessException("券模 " + cmd.couponCode()
                    + " 没有启用中的模板，发出去的券不会有任何规则、用户也用不了。请先去券模板页配置");
        }

        int quantity = Math.min(cmd.quantityOrOne(), MAX_QUANTITY_PER_MEMBER);
        int granted = 0;
        List<Long> skipped = new ArrayList<>();
        List<Long> failed = new ArrayList<>();

        for (Long memberId : memberIds) {
            GrantOutcome outcome = grantOne(memberId, cmd, quantity, operator);
            switch (outcome) {
                case GRANTED -> granted += quantity;
                case ALREADY -> skipped.add(memberId);
                case FAILED -> failed.add(memberId);
            }
        }

        log.info("【人工发券】操作人:{} 券模:{} 工单:{} 收件人:{} 发出:{} 跳过:{} 失败:{}",
                operator, cmd.couponCode(), cmd.bizRefId(), memberIds.size(),
                granted, skipped.size(), failed.size());
        return new ManualCouponGrantResult(granted, skipped, failed, template.getCouponName());
    }

    private enum GrantOutcome {
        /** 这次真发出去了 */
        GRANTED,
        /** 同一个工单号已经发过了 —— 幂等，不是失败 */
        ALREADY,
        /** 意外，需要人看 */
        FAILED
    }

    private GrantOutcome grantOne(Long memberId, ManualCouponGrantCmd cmd, int quantity, String operator) {
        String memberName;
        try {
            memberName = memberService.requireMemberName(memberId);
        } catch (RuntimeException e) {
            // 会员号不存在。整批拒绝在控制器那一层已经做过了，走到这里说明是刚被注销
            log.error("【人工发券】会员 {} 查不到，跳过。工单:{}", memberId, cmd.bizRefId(), e);
            return GrantOutcome.FAILED;
        }

        MemberCoupon last = null;
        for (int seq = 1; seq <= quantity; seq++) {
            /*
             * 幂等键：工单号 + 会员号 + 序号。
             *
             * 🔴 会员号必须进去 —— 只用「工单号:序号」的话，一个工单发给 5 个人
             *    会在第二个人身上撞唯一键，表现是「只有第一个人收到了」。
             *
             * 格式与商城发券一致（单号:序号），都能用 LIKE '单号:%' 反查回来。
             */
            String sourceBizId = cmd.bizRefId() + ":" + memberId + ":" + seq;
            MemberCoupon coupon = couponIssueService.newCoupon(new CouponIssueCmd(
                    cmd.couponCode(), memberId, memberName, SOURCE_TYPE_MANUAL, sourceBizId, null));
            coupon.setCreateBy(operator);
            try {
                memberCouponDao.insert(coupon);
                last = coupon;
            } catch (DuplicateKeyException e) {
                // uk_source 挡住的重复提交。第一张就撞上说明整个人都发过了
                if (seq == 1) {
                    log.info("【人工发券】会员 {} 在工单 {} 下已经发过了，跳过", memberId, cmd.bizRefId());
                    return GrantOutcome.ALREADY;
                }
                // 中间某一张撞上：上一次发到一半断了。剩下的补齐即可，不算失败
                log.warn("【人工发券】会员 {} 工单 {} 第 {} 张已存在，继续补后面的",
                        memberId, cmd.bizRefId(), seq);
            }
        }

        if (last != null) {
            notifyGranted(memberId, last, cmd.reason(), operator);
        }
        return GrantOutcome.GRANTED;
    }

    /**
     * 告诉用户一声。
     *
     * <p>🔴 <b>发了必须说</b>。券静悄悄躺进券包的话，客服为一次投诉补的那张券
     * 用户根本不知道 —— 补偿没有起到补偿的作用，他还会再投诉一次。
     *
     * <p>⚠️ 一个人发 N 张只发<b>一条</b>通知，不是 N 条。这和
     * {@code COUPON_EXPIRING} 那条「8 张券发一条」是同一个道理。
     *
     * <p>通知失败<b>不影响发券</b>：{@code NotificationService.send} 永不抛异常，
     * 券已经在用户手上了，为了一条消息回滚才是本末倒置。
     */
    private void notifyGranted(Long memberId, MemberCoupon coupon, String reason, String operator) {
        notificationService.send(NotifyRequest.of(NotificationTemplateEnum.COUPON_GRANTED, memberId)
                .param("couponName", coupon.getCouponName())
                // 客服填的那句话原样带给用户；没填就不硬编一句，留空比编一个理由好
                .param("reason", StringUtils.isBlank(reason) ? "" : reason.trim() + "。")
                .param("validEndTime", coupon.getValidEndTime() == null
                        ? "" : coupon.getValidEndTime().format(DATE))
                .bizRefId(coupon.getSourceBizId())
                .operator(operator)
                .build());
    }

    private void validate(ManualCouponGrantCmd cmd, List<Long> memberIds, String operator) {
        if (memberIds.isEmpty()) {
            throw new BusinessException("收件人不能为空");
        }
        if (memberIds.size() > MAX_RECIPIENTS) {
            throw new BusinessException(
                    "一次最多发给 " + MAX_RECIPIENTS + " 个会员，本次 " + memberIds.size() + " 个。"
                            + "要发给更多人请走活动配置 —— 那里有预算、有风控、有批次，"
                            + "而这个入口只是给客服补单用的。");
        }
        if (StringUtils.isBlank(cmd.couponCode())) {
            throw new BusinessException("券模编码不能为空");
        }
        if (StringUtils.isBlank(cmd.bizRefId())) {
            // 没有工单号就没有幂等键，双击一次就多发一批券出去
            throw new BusinessException("工单号 / 批次号不能为空 —— 它是防重发的唯一依据");
        }
        if (cmd.quantityOrOne() > MAX_QUANTITY_PER_MEMBER) {
            throw new BusinessException("每人最多 " + MAX_QUANTITY_PER_MEMBER + " 张");
        }
        if (StringUtils.isBlank(operator)) {
            // 人工发券不留痕，事后就回答不了「这批券是谁发的」
            throw new BusinessException("操作人不能为空");
        }
    }
}
