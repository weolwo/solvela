package solvela.member.api;

import java.util.List;

/**
 * 阶梯上的一档。
 *
 * @param gradeCode   等级
 * @param gradeName   等级名
 * @param threshold   门槛
 * @param reached     当前成长值够不够这一档
 * @param current     是不是用户此刻所在的那一档（🔴 与 reached 不是一回事：
 *                    保级缓冲期内他<b>在</b>白金，但成长值<b>够不着</b>白金）
 * @param privileges  这一档的权益，纯展示
 * @author alaric
 * @date 2026-09-20
 */
public record GradeLadderView(
        Integer gradeCode,
        String gradeName,
        Long threshold,
        Boolean reached,
        Boolean current,
        List<GradePrivilegeView> privileges) {
}
