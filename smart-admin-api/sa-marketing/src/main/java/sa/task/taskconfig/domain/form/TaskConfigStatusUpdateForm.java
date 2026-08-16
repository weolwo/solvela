package sa.task.taskconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 任务配置 状态变更表单（批量下线 / 重新上线共用）。
 *
 * <p>管理端用它替代删除：任务配置被 t_task_record 引用（记录里存着 task_config_id），
 * 删配置会让历史记录指向一条不存在的配置，运营复盘时查不到这条任务当初是怎么配的。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
public class TaskConfigStatusUpdateForm {

    @Schema(description = "任务配置id列表，单个操作也用列表传", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少选择一个任务")
    private List<Long> idList;

    @Schema(description = "目标状态：1-待生效, 2-生效中, 3-已下线", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标状态 不能为空")
    private Integer status;
}
