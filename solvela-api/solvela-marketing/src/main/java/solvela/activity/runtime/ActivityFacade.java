package solvela.activity.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.activity.ActivityConfig;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.ActivityDrawCmd;
import solvela.marketing.api.ActivityPlayKeys;
import solvela.marketing.api.ActivityRuleView;
import solvela.marketing.api.DrawRejectReason;
import solvela.marketing.api.DrawResultView;
import solvela.activity.service.ActivityConfigService;
import solvela.enums.ActivityStatusEnum;
import solvela.exception.BusinessException;
import solvela.activity.spi.ActivityPlayMountProvider;
import solvela.scriptengine.runtime.ScriptRuntime;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptRefPoint;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * C 端公共活动契约的实现。{@link ActivityApi} 的唯一实现类。
 *
 * <h3>它做的事：校验 → 进脚本 → 把结果搬出来</h3>
 * <pre>
 *   draw(cmd)
 *     ├─ Java：活动存在吗、上线了吗、在参与窗内吗
 *     └─ 脚本 ACTIVITY_PLAY（挂在 activity_code 上）
 *          ├─ 脚本按次数/身份/时段算出 poolCode
 *          └─ 脚本调用 draw_draw(poolCode) → 抽奖引擎
 * </pre>
 *
 * <h3>🔴 校验必须在 Java 里，不能交给脚本</h3>
 * 脚本是<b>运营写的</b>。把「活动上没上线」「在不在时间窗内」交给脚本判，
 * 漏一句就是下线的活动照样能抽 —— 而且是静默的，不会有任何报错。
 * 脚本可以在这之上再加业务限制，但不能是唯一的一道。
 *
 * <h3>幂等只有一处，在引擎里</h3>
 * 这里<b>刻意不再做一次 requestId 去重</b>。抽奖引擎已经按 requestId 去重，
 * 而本层到引擎之间只有活动校验和脚本判定 —— 两者都没有副作用，重跑无害
 * （引擎的副作用约束保证脚本里最多只发生一次有副作用的调用）。
 *
 * <p>再加一道的代价是实打实的：同一个语义两把锁，要么用同一个 key 让第二把锁自己拒绝自己，
 * 要么用两个 key 变成两处要一起维护的幂等语义。而收益是零 —— 中间没有需要保护的副作用。
 *
 * <h3>会员校验不在这里</h3>
 * 「这个会员存不存在、是不是被冻结」在<b>网关</b>解析令牌时就做完了
 * （{@code MemberPrincipalLoader} 只放行状态正常的会员），抽奖引擎还会再要一次会员号的真实性。
 * 在这里插第三道等于让活动域反向依赖会员域，换来的只是重复一遍已经成立的结论。
 *
 * @Date 2026-08-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityFacade implements ActivityApi {

    private final ActivityConfigService activityConfigService;

    private final ActivityRuntimeService activityRuntimeService;

    private final ScriptRuntime scriptRuntime;

    /**
     * 各玩法回答「脚本挂在哪」。由 solvela-marketing 注册（依赖倒置，见 {@link ActivityPlayMountProvider}）。
     */
    private final ObjectProvider<ActivityPlayMountProvider> playMountProviders;

    @Override
    public ActivityRuleView getActivityRule(String activityCode) {
        return activityRuntimeService.getActivityRule(activityCode);
    }

    @Override
    public DrawResultView draw(ActivityDrawCmd cmd) {
        ActivityConfig activity = activityConfigService.getByActivityCode(cmd.activityCode());
        if (activity == null) {
            return DrawResultView.ofReject(DrawRejectReason.ACTIVITY_NOT_FOUND);
        }
        if (!joinable(activity, LocalDateTime.now())) {
            return DrawResultView.ofReject(DrawRejectReason.ACTIVITY_NOT_OPEN);
        }

        // 脚本挂在哪由玩法自己说：抽奖挂在抽奖配置上，没有玩法配置的活动仍挂在活动上
        ActivityPlayMountProvider.PlayMount mount = resolveMount(activity);
        if (mount == null) {
            log.warn("[活动-参与] 活动是 {} 类型但没有可用的玩法配置, activityCode: {}",
                    activity.getActivityType(), activity.getActivityCode());
            return DrawResultView.ofReject(DrawRejectReason.NO_PLAY_CONFIG);
        }

        Optional<Object> result = scriptRuntime.evaluate(
                mount.point(), mount.refId(), playContext(activity, cmd), Object.class);

        if (result.isEmpty()) {
            // 玩法配置有了却没挂编排脚本 —— 运营配置没做完，不是用户的问题，所以是 reject 不是异常
            log.warn("[活动-参与] 未挂玩法编排脚本, 挂载点: {}, 业务对象: {}",
                    mount.point().getTitle(), mount.refId());
            return DrawResultView.ofReject(DrawRejectReason.NO_PLAY_SCRIPT);
        }
        if (result.get() instanceof DrawResultView view) {
            return view;
        }
        // 脚本返回了别的类型：挂错脚本或脚本写错了，是配置事故不是业务失败，抛出去让它变成 5xx
        throw new BusinessException(String.format(
                "活动 [%s] 的玩法编排脚本返回了 %s，而调用方要的是抽奖结果。"
                        + "多半是把别的场景的脚本挂到了 ACTIVITY_PLAY 上，或者脚本最后一步没有返回抽奖函数的结果。",
                activity.getActivityCode(), result.get().getClass().getSimpleName()));
    }

    /**
     * 这个活动的玩法编排脚本挂在哪。
     *
     * <p>找不到对应玩法的实现时<b>退回 {@code ACTIVITY_PLAY} / 活动编码</b> ——
     * 这正是本 SPI 出现之前的行为，BASIC 这类没有玩法配置的活动一直走这条路。
     *
     * <p>⚠️ 「没有实现」与「有实现但返回 null」是两件事：前者说明这个玩法还没有配置层，
     * 退回老路是对的；后者说明这个玩法有配置层、但这个活动的配置没建或被关了，
     * 那必须报出来，不能悄悄退回去挂到活动上 —— 否则运营关掉抽奖配置之后活动照样能抽。
     */
    private ActivityPlayMountProvider.PlayMount resolveMount(ActivityConfig activity) {
        ActivityPlayMountProvider provider = playMountProviders.stream()
                .filter(item -> item.supportType() != null
                        && item.supportType().getValue().equals(activity.getActivityType()))
                .findFirst()
                .orElse(null);
        if (provider == null) {
            return new ActivityPlayMountProvider.PlayMount(
                    ScriptRefPoint.ACTIVITY_PLAY, activity.getActivityCode());
        }
        return provider.resolve(activity.getActivityCode());
    }

    /**
     * 组装脚本执行上下文。
     *
     * <p>🔴 memberId / activityCode <b>两条通道都绑</b>，不是冗余：
     * <ul>
     *   <li>脚本变量通道那份是给<b>脚本读</b>的（判断次数、圈人群），脚本可以随便改它；</li>
     *   <li>内部通道那份是给 <b>Java 函数用</b>的权威值，脚本看不见也改不掉。</li>
     * </ul>
     * 只绑脚本变量的话，一段 {@code memberId = 10086; return draw_draw('POOL_A');}
     * 就能替别人抽奖。内部通道的读写统一走 {@link ActivityPlayContext}，
     * 键的定义见 {@link ActivityPlayKeys}。
     */
    private static EngineContext playContext(ActivityConfig activity, ActivityDrawCmd cmd) {
        EngineContext context = EngineContext.create()
                // --- 脚本可见：给运营写判断用 ---
                .bind("memberId", cmd.memberId())
                .bind("activityCode", activity.getActivityCode())
                .bind("activityType", activity.getActivityType())
                // 客户端点的是「单抽」还是「十连抽」。⚠️ 只是【意愿】——
                // 脚本拿它跟剩余次数一比，算出真正的次数再传给抽奖函数。
                // 脚本可以改这个变量，改了也只影响它自己传进去的那个数，这正是我们要的
                .bind("times", cmd.times())
                // 空 Map 而不是 null：场景契约要求 params 必填，而「前端没传自定义参数」是常态。
                // 让脚本去判 null 是把一个本可以消灭的分支塞给运营
                .bind("params", cmd.params() == null ? Map.of() : cmd.params());

        // --- 脚本不可见：函数用的权威值 ---
        // 🔴 内部通道的读写统一走 ActivityPlayContext，别处不要直接碰 ActivityPlayKeys ——
        //    通道里有哪些键、少了算不算错，只有那一个类知道
        return new ActivityPlayContext(cmd.memberId(), activity.getActivityCode(), cmd.requestId())
                .bindInto(context);
    }

    /**
     * 此刻还能不能参与。
     *
     * <p>判据是 <b>{@code dataEndTime}</b> 而不是 {@code endTime} —— 数据截止之后活动还在、
     * 已中的奖还能领，只是不再受理新的参与。{@code dataEndTime} 为空时退回 {@code endTime}，
     * 与加那一列之前的行为一致。
     *
     * <p>与 {@code ActivityRuleView#joinable} 是同一套判据，但<b>那个是给展示用的</b>
     * （按钮要不要置灰，算的是客户端的时钟）。这里才是准入。两处判据必须一致，
     * 改一处记得改另一处。
     */
    private static boolean joinable(ActivityConfig activity, LocalDateTime now) {
        if (activity.getStatus() != ActivityStatusEnum.ONLINE) {
            return false;
        }
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            return false;
        }
        LocalDateTime deadline = activity.getDataEndTime() == null
                ? activity.getEndTime() : activity.getDataEndTime();
        return deadline == null || !now.isAfter(deadline);
    }
}
