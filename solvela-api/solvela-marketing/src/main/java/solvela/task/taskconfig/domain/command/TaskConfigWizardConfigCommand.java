package solvela.task.taskconfig.domain.command;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务配置向导 主表配置（提交 DTO 的 taskConfig 节点，对应 t_task_config）
 *
 * @Author alaric
 * @Date 2026-07-19
 */
@Data
public class TaskConfigWizardConfigCommand {

    /** 所属活动大类编码 */
    private String activityCode;

    /** 任务模板编码 */
    private String templateCode;

    /** 任务名称 */
    private String taskName;

    /** 触发事件 */
    private String triggerEvent;

    /** 任务分组：NEWBIE, DAILY, PROMO, VIP */
    private String taskGroup;

    /** 任务副标题，存入 ui_config */
    private String taskDesc;

    /** 详细规则说明，存入 ui_config */
    private String ruleDesc;

    /** 目标人群：ALL, NEW_MEMBER, OLD_MEMBER */
    private String targetAudience;

    /** 开始时间，长期有效时为空 */
    private LocalDateTime startTime;

    /** 结束时间，长期有效时为空 */
    private LocalDateTime endTime;

    /** 排序权重 */
    private Integer sortWeight;

    /** 跳转地址 */
    private String actionUrl;

    /** 参与频次：ONCE, DAILY, WEEKLY, UNLIMITED */
    private String limitType;

    /** 限制次数 */
    private Integer limitCount;

    /** UI配置：badge/图片类参数(image_upload)等 */
    private Map<String, Object> uiConfig;

    /** 规则参数：模板 ui_schema 收集的非图片参数 */
    private Map<String, Object> ruleConfig;
}
