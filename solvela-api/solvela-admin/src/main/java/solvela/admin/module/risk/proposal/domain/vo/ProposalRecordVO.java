package solvela.admin.module.risk.proposal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 提案表 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */

@Data
public class ProposalRecordVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "提案单号，服务端生成，对外唯一标识")
    private String tradeNo;

    @Schema(description = "会员号")
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    @Schema(description = "会员账号（下单当时的快照）")
    private String memberName;

    @Schema(description = "SCORE/BALANCE/COUPON/PHYSICAL")
    private String assetType;

    @Schema(description = "资产引用：券模/SKU，值类资产为空")
    private String assetRef;

    @Schema(description = "资产展示名（券名/商品名），由营销侧传入")
    private String assetName;

    @Schema(description = "发放金额/积分数")
    private BigDecimal amount;

    @Schema(description = "发放数量，扣 used_quota 用")
    private Integer quantity;

    @Schema(description = "来源：TASK(任务), DRAW(抽奖), MANUAL(人工)")
    private String sourceType;

    @Schema(description = "来源单号(task_record_id 或 draw_log_trace_id)")
    private String sourceBizId;

    @Schema(description = "优惠配置ID")
    private Long promotionConfigId;

    @Schema(description = "状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截")
    private Integer status;

    @Schema(description = "执行失败/风控拦截原因")
    private String remark;

    @Schema(description = "风控拦截分类（对齐 RiskBlockCode）：仅 status=80 时有值")
    private String riskCode;

    @Schema(description = "一审人")
    private String firstReviewer;

    @Schema(description = "一审时间")
    private LocalDateTime firstReviewTime;

    @Schema(description = "二审人")
    private String secondReviewer;

    @Schema(description = "二审时间")
    private LocalDateTime secondReviewTime;

    @Schema(description = "审核意见/驳回理由")
    private String reviewComment;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
