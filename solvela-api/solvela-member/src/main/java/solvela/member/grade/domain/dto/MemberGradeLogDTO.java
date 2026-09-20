package solvela.member.grade.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条等级变更留痕。
 *
 * <p>🔴 {@code periodValue} 是<b>变更那一刻</b>的快照，不是现在的值。
 * 事后拿当前成长值去反推会因为周期已经翻篇而永远算不回去 ——
 * 而那正是客诉要问的那个数。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
public class MemberGradeLogDTO {

    private Long id;

    private Long memberId;

    private String memberName;

    private Integer oldGrade;

    private String oldGradeName;

    private Integer newGrade;

    private String newGradeName;

    /** UPGRADE / DOWNGRADE / KEEP / MANUAL / RISK_REVOKE */
    private String changeType;

    /** 变更时的周期成长值快照 */
    private Long periodValue;

    private String reason;

    /** 操作人。系统变更为空 */
    private String operator;

    private LocalDateTime createTime;
}
