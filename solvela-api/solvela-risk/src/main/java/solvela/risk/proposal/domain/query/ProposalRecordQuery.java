package solvela.risk.proposal.domain.query;

import solvela.enums.ProposalStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

/**
 * 风控提案分页查询的<b>领域参数</b>。形状与管理端的 {@code ProposalRecordQuery} 目前一致，
 * 但<b>变更的理由不同</b>：Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}（全项目第一个改造的样板）。
 * 这里刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class ProposalRecordQuery extends PageParam {

    /**
     * 会员号（精确匹配，走 member_id 索引）。
     *
     * <p>🔴 这里<b>刻意不再收账号</b>：v3.71.0 之后 {@code member_name} 只是展示快照、
     * 身上没有任何索引，拿它当查询条件必是全表扫；而它又是可改的，
     * 用户改名之后按旧名字查等于查不到 —— 「不报错，只是查不到了」正是这次换键要消灭的。
     * 后台要按账号找人，先经 {@code MemberService.getMemberId} 换成会员号。
     */
    private Long memberId;

    /** 更新时间 */
    private LocalDate updateTimeBegin;

    /** 更新时间 */
    private LocalDate updateTimeEnd;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

    /** 优惠配置ID */
    private Long promotionConfigId;

    /** 状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截 */
    private ProposalStatusEnum status;

    /** 来源：TASK(任务), DRAW(抽奖), MANUAL(人工) */
    private String sourceType;

    /** 来源单号(taskRecordId 或 drawLogTraceId) */
    private String sourceBizId;

    /** 一审人 */
    private String firstReviewer;

    /** 提案单号，服务端生成，对外唯一标识 */
    private String tradeNo;

    /** SCORE/BALANCE/COUPON/PHYSICAL */
    private String assetType;

}
