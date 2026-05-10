package net.lab1024.sa.risk.proposal.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提案表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class ProposalRecordQueryForm extends PageParam {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "更新时间")
    private LocalDate updateTimeBegin;

    @Schema(description = "更新时间")
    private LocalDate updateTimeEnd;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

    @Schema(description = "优惠配置ID")
    private Long promotionConfigId;

    @Schema(description = "状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截")
    private Integer status;

    @Schema(description = "来源：TASK(任务), DRAW(抽奖), MANUAL(人工)")
    private String sourceType;

    @Schema(description = "来源单号(taskRecordId 或 drawLogTraceId)")
    private String sourceBizId;

    @Schema(description = "一审人")
    private String firstReviewer;

}
