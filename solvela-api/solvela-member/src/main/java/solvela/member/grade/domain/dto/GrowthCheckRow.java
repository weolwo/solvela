package solvela.member.grade.domain.dto;

import lombok.Data;

/**
 * 对账扫描出来的一行：主表记的值 vs 流水求和。
 *
 * <p>它<b>不叫 Drift</b> —— 扫出来的绝大多数是对得上的。
 * 叫 Drift 会让人以为「有这个对象就是出问题了」，
 * 而判断对不对是调用方的事（{@link #drifted()}）。
 *
 * @author alaric
 * @date 2026-09-21
 */
@Data
public class GrowthCheckRow {

    private Long memberId;

    /** 当前周期的 tag（period_start 的 yyyyMMdd） */
    private String periodTag;

    /** 主表 t_member_growth.current_period_value —— 冗余的那个 */
    private Long recorded;

    /** 流水求和 —— <b>它才是真相</b>：append-only、带幂等唯一键 */
    private Long logSum;

    public boolean drifted() {
        return !java.util.Objects.equals(recorded, logSum);
    }

    /** 主表比流水多了多少（负数表示少了） */
    public long diff() {
        return (recorded == null ? 0L : recorded) - (logSum == null ? 0L : logSum);
    }
}
