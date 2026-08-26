package solvela.task.prizemapping.domain.form;

import solvela.base.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务阶段与奖励映射表 分页查询表单
 *
 * <p>这张表没有活动维度的字段，活动/任务名/任务状态三个条件都落在主表 t_task_config 上，
 * 由 mapper 里的 JOIN 承接 —— 运营记得住活动编码，记不住 task_config_id。
 *
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class TaskPrizeMappingQueryForm extends PageParam {

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "任务名称（模糊）")
    private String taskName;

    @Schema(description = "任务状态：1-待生效, 2-生效中, 3-已下线")
    private Integer taskStatus;

    @Schema(description = "任务配置ID")
    private Long taskConfigId;

    @Schema(description = "奖励编码")
    private String prizeCode;

}
