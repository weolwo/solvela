package solvela.member.grade.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一个会员的成长值与等级现状。
 *
 * <h3>🔴 {@code gradeName} / {@code nextGap} 是查出来之后拼的，不是 JOIN 出来的</h3>
 * 等级配置只有几行且几乎不变，一次性查出来在内存里映射，比每行去 JOIN 一次划算。
 * 更重要的是：<b>「还差多少升级」这个数必须和判级用同一份配置算</b> ——
 * 如果它是 SQL 里另写一遍的表达式，某天改判级规则时一定会漏掉它，
 * 于是页面上会出现「还差 0 点升级」但人就是没升。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
public class MemberGrowthDTO {

    private Long memberId;

    /** 账号。运营认的是它 */
    private String memberName;

    private String nickname;

    private Integer currentGrade;

    /** 等级名，服务层按配置填 */
    private String gradeName;

    private LocalDateTime gradeSince;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    private Long currentPeriodValue;

    private Long totalValue;

    private LocalDateTime protectUntil;

    private Integer protectGrade;

    /** 下一级。已经是最高档时为 {@code null} */
    private Integer nextGrade;

    private String nextGradeName;

    /**
     * 距下一级还差多少成长值。已经是最高档时为 {@code null}。
     *
     * <p>⚠️ 和 {@code level} 一样只是<b>此刻</b>的快照：人工调级之后这个数会
     * 和等级对不上（比如被调到白金但成长值只够银卡），那不是 bug ——
     * 见 {@code MemberGradeAdminService.adjustLevel} 的说明。
     */
    private Long nextGap;

    /** 在保级缓冲期内吗。服务层按 {@code protectUntil} 与当前时刻算 */
    private Boolean inProtect;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
