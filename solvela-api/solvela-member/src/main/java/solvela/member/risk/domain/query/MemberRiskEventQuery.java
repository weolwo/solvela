package solvela.member.risk.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

import java.time.LocalDateTime;

/**
 * 决策流水的查询条件。
 *
 * <h3>🔴 时间范围不是可选项</h3>
 * {@code t_member_risk_event} 按月分区，而分区裁剪<b>只看 WHERE 里有没有分区列</b>
 * （与索引无关）。不带时间的查询会在每个分区上各探一次索引 ——
 * 现在只有几个分区，所以慢得不明显；保留期攒满之后就是 6 倍。
 * 所以调用方不传时由 Service 兜一个默认区间，而不是真的不限时间。
 *
 * @Date 2026-10-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberRiskEventQuery extends PageParam {

    private Long memberId;

    /**
     * 身份摘要（64 位 hex）。
     *
     * <p>🔴 接入层收的是<b>明文</b>手机号 / 邮箱，在那一侧算成摘要再传进来 ——
     * 明文不进域的查询条件，也就不会出现在 SQL 日志里。
     * 这也是「查无此人的尝试」唯一的聚合口径（那些行没有 member_id）。
     */
    private String identityHashHex;

    private String clientIp;

    private String deviceId;

    /** 生效结论，见 MemberRiskDecisionEnum */
    private Integer decision;

    /** 最终结局，见 MemberRiskOutcomeEnum */
    private Integer outcome;

    /** 只看「生效结论与陪跑结论不一致」的 —— 「把这条规则切到生效会多打扰多少人」靠它 */
    private Boolean shadowDiffOnly;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
