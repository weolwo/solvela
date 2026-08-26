package solvela.risk.proposal.domain.dto;


import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 风控提案列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * 完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class ProposalRecordDTO {


    private Long id;

    /** 提案单号，服务端生成，对外唯一标识 */
    private String tradeNo;

    /** 会员号 */
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    private String memberName;

    /** SCORE/BALANCE/COUPON/PHYSICAL */
    private String assetType;

    /** 资产引用：券模/SKU，值类资产为空 */
    private String assetRef;

    /** 资产展示名（券名/商品名），由营销侧传入 */
    private String assetName;

    /** 发放金额/积分数 */
    private BigDecimal amount;

    /** 发放数量，扣 used_quota 用 */
    private Integer quantity;

    /** 来源：TASK(任务), DRAW(抽奖), MANUAL(人工) */
    private String sourceType;

    /** 来源单号(task_record_id 或 draw_log_trace_id) */
    private String sourceBizId;

    /** 优惠配置ID */
    private Long promotionConfigId;

    /** 状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截 */
    private Integer status;

    /** 执行失败/风控拦截原因 */
    private String remark;

    /** 风控拦截分类（对齐 RiskBlockCode）：仅 status=80 时有值 */
    private String riskCode;

    /** 一审人 */
    private String firstReviewer;

    /** 一审时间 */
    private LocalDateTime firstReviewTime;

    /** 二审人 */
    private String secondReviewer;

    /** 二审时间 */
    private LocalDateTime secondReviewTime;

    /** 审核意见/驳回理由 */
    private String reviewComment;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
