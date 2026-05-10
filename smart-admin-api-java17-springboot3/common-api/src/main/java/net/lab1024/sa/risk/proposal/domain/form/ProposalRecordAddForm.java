package net.lab1024.sa.risk.proposal.domain.form;

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

    @Schema(description = "租户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户ID 不能为空")
    private String tenantId;

    @Schema(description = "会员名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会员名 不能为空")
    private String memberName;

    @Schema(description = "来源：TASK(任务), DRAW(抽奖), MANUAL(人工)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "来源：TASK(任务), DRAW(抽奖), MANUAL(人工) 不能为空")
    private String sourceType;

    @Schema(description = "来源单号(task_record_id 或 draw_log_trace_id)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "来源单号(task_record_id 或 draw_log_trace_id) 不能为空")
    private String sourceBizId;

    @Schema(description = "优惠配置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "优惠配置ID 不能为空")
    private Long promotionConfigId;

    @Schema(description = "优惠金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "优惠金额 不能为空")
    private BigDecimal promotionValue;

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

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}