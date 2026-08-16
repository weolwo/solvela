package sa.task.tasktemplate.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务模板表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class TaskTemplateQueryForm extends PageParam {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型)")
    private String taskType;

    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
