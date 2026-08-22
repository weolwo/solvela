package sa.task.tasktemplate.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务模板表 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */

@Data
public class TaskTemplateVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型)")
    private String taskType;

    @Schema(description = "默认触发事件：模板建议值，向导中可覆盖")
    private String triggerEvent;

    @Schema(description = "前端渲染规则")
    private String uiSchema;

    @Schema(description = "QLExpress脚本")
    private String ruleScript;

    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer status;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
