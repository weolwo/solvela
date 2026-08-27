package solvela.admin.module.task.tasktemplate.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 任务模板 启用/禁用表单（单个开关与批量禁用共用）。
 *
 * <p>不复用 {@code TaskTemplateUpdateCommand}：那个表单要求 uiSchema 一起回传，
 * 只想停一个模板却要把整套渲染规则带上，既冗余又给了顺手改错的机会。写法对齐 PrizeStatusUpdateForm。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
public class TaskTemplateStatusUpdateForm {

    @Schema(description = "模板id列表，单个操作也用列表传", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少选择一个模板")
    private List<Long> idList;

    @Schema(description = "目标状态：1-启用, 0-禁用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标状态 不能为空")
    private Integer status;
}
