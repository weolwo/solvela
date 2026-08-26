package solvela.risk.proposal.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 提案表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */

@Data
public class ProposalRecordAddForm {

    // 单号由提案域自己发（saveProposal 里生成），调用方不该也不能指定 —— 它是本域对外的凭证。
    // 这里若保留 @NotBlank，将来任何 Controller 暴露提案创建接口都会被误挡在门外
    @Schema(description = "提案单号，服务端生成，调用方无需传入", accessMode = Schema.AccessMode.READ_ONLY)
    private String tradeNo;

    /**
     * 会员号 —— 关联键。调用方只需给它，账号快照由服务端查会员表补
     * （见 {@code MemberService.requireMemberName}），这样快照与会员号<b>不可能对不上</b>。
     */
    @Schema(description = "会员号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号 不能为空")
    private Long memberId;

    @Schema(description = "SCORE/BALANCE/COUPON/PHYSICAL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "SCORE/BALANCE/COUPON/PHYSICAL 不能为空")
    private String assetType;

    @Schema(description = "资产引用：券模/SKU，值类资产为空")
    private String assetRef;

    @Schema(description = "资产展示名（券名/商品名）：账务侧发券/发货时直接用，不传则回退用 assetRef")
    private String assetName;

    @Schema(description = "发放金额/积分数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "发放金额/积分数 不能为空")
    private BigDecimal amount;

    @Schema(description = "发放数量，扣 used_quota 用；不传按 1 计")
    private Integer quantity;

    @Schema(description = "来源：TASK(任务), DRAW(抽奖), MANUAL(人工)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "来源：TASK(任务), DRAW(抽奖), MANUAL(人工) 不能为空")
    private String sourceType;

    @Schema(description = "来源单号(task_record_id 或 draw_log_trace_id)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "来源单号(task_record_id 或 draw_log_trace_id) 不能为空")
    private String sourceBizId;

    @Schema(description = "优惠配置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "优惠配置ID 不能为空")
    private Long promotionConfigId;

    @Schema(description = "状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截")
    private Integer status;

    @Schema(description = "执行失败/风控拦截原因")
    private String remark;

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

}