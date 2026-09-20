package solvela.member.grade.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条成长值流水。
 *
 * <p>{@code baseValue} 与 {@code multiplier} 两列一起下发，而不是只给 {@code delta} ——
 * 客诉的原话是「我这笔为什么是 200 不是 100」，答案只能靠这两个数说清楚。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
public class MemberGrowthLogDTO {

    private Long id;

    private Long memberId;

    /** 本次增减（已乘倍率） */
    private Long delta;

    /** 倍率之前的基数 */
    private Long baseValue;

    /** 1-常态, 2-保级缓冲期 */
    private Integer multiplier;

    /** 变动后的周期累计值（对账锚点） */
    private Long afterPeriodValue;

    private String source;

    /** 上游业务类型。白名单判据就是它 */
    private String bizType;

    /** 上游业务单号：幂等键 */
    private String bizId;

    /** 计入哪个周期。保级期的加速计入【上一周期】 */
    private String periodTag;

    private String remark;

    private LocalDateTime createTime;
}
