package solvela.task.tasktemplate.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务模板列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class TaskTemplateDTO {


    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型) */
    private String taskType;

    /** 默认触发事件：模板建议值，向导中可覆盖 */
    private String triggerEvent;

    /** 前端渲染规则 */
    private String uiSchema;

    /** 状态：0-禁用, 1-启用 */
    private Integer status;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
