package net.lab1024.sa.risk.proposal.domain.vo;

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

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "来源：TASK(任务), DRAW(抽奖), MANUAL(人工)")
    private String sourceType;

    @Schema(description = "来源单号(task_record_id 或 draw_log_trace_id)")
    private String sourceBizId;

    @Schema(description = "优惠配置ID")
    private Long promotionConfigId;

    @Schema(description = "优惠金额")
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

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
