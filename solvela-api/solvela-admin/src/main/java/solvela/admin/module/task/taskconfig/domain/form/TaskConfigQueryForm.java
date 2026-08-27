package solvela.admin.module.task.taskconfig.domain.form;

import solvela.base.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务配置表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:55:10
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class TaskConfigQueryForm extends PageParam {

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "模板Code")
    private String templateCode;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "触发事件：取值来自注册表 t_task_event，不是固定枚举")
    private String triggerEvent;

    @Schema(description = "任务状态：1-待生效, 2-生效中, 3-已下线（见 TaskConst.CONFIG_STATUS_*）")
    private Integer status;

}
