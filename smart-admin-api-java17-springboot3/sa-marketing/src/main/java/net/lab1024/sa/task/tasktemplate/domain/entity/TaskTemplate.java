package net.lab1024.sa.task.tasktemplate.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务模板表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */

@Data
@TableName("t_task_template")
public class TaskTemplate {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 模板编码
     */
    private String templateCode;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型)
     */
    private String taskType;

    /**
     * 默认触发事件：模板建议值，向导中可覆盖
     */
    private String triggerEvent;

    /**
     * 前端渲染规则
     */
    private String uiSchema;

    /**
     * QLExpress脚本
     */
    private String ruleScript;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
