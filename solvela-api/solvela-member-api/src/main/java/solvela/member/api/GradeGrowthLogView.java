package solvela.member.api;

import java.time.LocalDateTime;

/**
 * 一条成长值明细。
 *
 * <p>🔴 {@code baseValue} 与 {@code multiplier} 必须一起下发：
 * 用户在缓冲期看到「+200」而自己只做了一件值 100 的事，第一反应是系统算错了。
 * 把「100 × 2」摆出来，那条加速规则才真的被感知到 —— 否则翻倍等于白送。
 *
 * @author alaric
 * @date 2026-09-20
 */
public record GradeGrowthLogView(
        Long delta,
        Long baseValue,
        Integer multiplier,
        String remark,
        LocalDateTime createTime) {
}
