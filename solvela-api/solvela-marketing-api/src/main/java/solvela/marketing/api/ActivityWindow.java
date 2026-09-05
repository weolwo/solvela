package solvela.marketing.api;

import solvela.enums.ActivityStatusEnum;

import java.time.LocalDateTime;

/**
 * 活动时间窗的判据。<b>整条链路只有这一份实现。</b>
 *
 * <h3>为什么抽出来</h3>
 * 「此刻能不能参与」同时被两个 view 需要：{@link ActivityRuleView}（详情页按钮要不要置灰）
 * 与 {@link ActivityBriefView}（列表页的「进行中 / 未开始」角标）。
 * 各写一遍的代价不是重复几行，而是<b>会漂</b> —— 而这条规则漂了的表现是
 * 「列表说进行中、点进去说没开始」，或者更糟：列表把一个已经数据截止的活动显示成可参与，
 * 用户点进去被服务端拒绝，看起来像系统坏了。
 *
 * <p>放在契约模块里而不是域里，是因为<b>两个 view 都在这</b>，而域侧判准入用的是
 * 自己的实体（{@code ActivityFacade.joinable(ActivityConfig, now)}）——
 * 那一份是真正的守卫，这一份只服务于展示。
 *
 * <h3>⚠️ 这里算出来的结果只能用于展示</h3>
 * 真正的准入判定必须在服务端用<b>服务端时钟</b>做。网关下发的 joinable 也是服务端算的，
 * 客户端不该自己调这个 —— 它算的是客户端的时钟，改一下系统时间就能点开一个没开始的活动。
 */
public final class ActivityWindow {

    private ActivityWindow() {
    }

    /**
     * 此刻还能不能<b>参与</b>（抽奖、累计任务进度）。
     *
     * <p>判据是 {@code dataEndTime} 而不是 {@code endTime} —— 这正是加那一列的原因：
     * 数据截止之后活动还在，只是不再受理新的参与。为空时退回 {@code endTime}。
     */
    public static boolean joinable(ActivityStatusEnum status, LocalDateTime startTime,
                                   LocalDateTime dataEndTime, LocalDateTime endTime,
                                   LocalDateTime now) {
        LocalDateTime deadline = dataEndTime == null ? endTime : dataEndTime;
        return status == ActivityStatusEnum.ONLINE
                && (startTime == null || !now.isBefore(startTime))
                && (deadline == null || !now.isAfter(deadline));
    }

    /**
     * 此刻还能不能<b>领奖</b>。数据截止之后到活动结束之前，这里仍然是 true。
     */
    public static boolean claimable(ActivityStatusEnum status, LocalDateTime startTime,
                                    LocalDateTime endTime, LocalDateTime now) {
        return status == ActivityStatusEnum.ONLINE
                && (startTime == null || !now.isBefore(startTime))
                && (endTime == null || !now.isAfter(endTime));
    }
}
