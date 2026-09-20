package solvela.admin.module.member.grade.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

/**
 * 成长值流水 分页查询表单。
 *
 * <p>🔴 {@code memberId} 必填：这张表跟着入账量一起长，全表翻页没有业务场景。
 * 服务层也有一道同样的校验 —— 端上的 {@code @NotNull} 是给运营看的提示，
 * 领域侧那道才是防线。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberGrowthLogQueryForm extends PageParam {

    @Schema(description = "会员号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请先选定会员")
    private Long memberId;

    @Schema(description = "来源：SCORE_EARNED 等")
    private String source;

    @Schema(description = "上游业务类型：PROPOSAL_REWARD 等")
    private String bizType;

    @Schema(description = "计入周期（period_start 的 yyyyMMdd）")
    private String periodTag;

    @Schema(description = "发生时间-开始")
    private LocalDate createTimeBegin;

    @Schema(description = "发生时间-结束")
    private LocalDate createTimeEnd;
}
