package solvela.member.api;

/**
 * 一条等级权益 —— <b>纯展示</b>。
 *
 * <p>🔴 它不驱动任何逻辑。真正的权益靠任务人群（{@code GRADE_GTE_N}）、
 * 脚本（{@code member_gradeAtLeast}）、商城价格模型实现。
 * 这里回答的是另一个问题：<b>用户在等级页看见自己在保什么</b>。
 *
 * @author alaric
 * @date 2026-09-20
 */
public record GradePrivilegeView(
        String privilegeCode,
        String privilegeName,
        String description,
        Long iconFileId,
        String actionUrl) {
}
