package solvela.member.grade.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

/**
 * 成长值流水分页查询。
 *
 * <p>🔴 {@code memberId} 是<b>必填</b>，由 Service 兜底校验：
 * 全表扫成长值流水没有任何业务场景，而它会随着入账量一起长成大表。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberGrowthLogQuery extends PageParam {

    /** 会员号：必填 */
    private Long memberId;

    /** 来源：SCORE_EARNED 等 */
    private String source;

    /** 上游业务类型：PROPOSAL_REWARD 等 —— 排查「这笔为什么没算成长值」时按它筛 */
    private String bizType;

    /** 计入哪个周期（period_start 的 yyyyMMdd） */
    private String periodTag;

    private LocalDate createTimeBegin;

    private LocalDate createTimeEnd;
}
