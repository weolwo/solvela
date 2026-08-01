package net.lab1024.sa.task.taskevent.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 任务事件注册表 查询表单
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskEventQueryForm extends PageParam {

    @Schema(description = "事件编码（模糊）")
    private String eventCode;

    @Schema(description = "展示名（模糊）")
    private String eventName;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer status;
}
