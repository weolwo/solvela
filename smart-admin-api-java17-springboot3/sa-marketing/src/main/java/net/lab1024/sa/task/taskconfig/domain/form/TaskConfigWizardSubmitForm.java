package net.lab1024.sa.task.taskconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务配置向导 提交表单（主子表：t_task_config + t_task_prize_mapping，一个事务内落库）
 *
 * @Author alaric
 * @Date 2026-07-19
 */
@Data
public class TaskConfigWizardSubmitForm {

    @Schema(description = "所属活动大类编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "所属活动大类 不能为空")
    private String activityCode;

    @Schema(description = "任务模板编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务模板 不能为空")
    private String templateCode;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务名称 不能为空")
    private String taskName;

    @Schema(description = "触发事件", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "触发事件 不能为空")
    private String triggerEvent;

    @Schema(description = "任务分组：NEWBIE, DAILY, PROMO, VIP")
    private String taskGroup;

    @Schema(description = "任务副标题，存入 ui_config")
    private String taskDesc;

    @Schema(description = "详细规则说明，存入 ui_config")
    private String ruleDesc;

    @Schema(description = "目标人群：ALL, NEW_MEMBER, OLD_MEMBER")
    private String targetAudience;

    @Schema(description = "开始时间，长期有效时为空")
    private LocalDateTime startTime;

    @Schema(description = "结束时间，长期有效时为空")
    private LocalDateTime endTime;

    @Schema(description = "排序权重")
    private Integer sortWeight;

    @Schema(description = "跳转地址")
    private String actionUrl;

    @Schema(description = "参与频次：ONCE, DAILY, WEEKLY, UNLIMITED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "参与频次 不能为空")
    private String limitType;

    @Schema(description = "限制次数")
    private Integer limitCount;

    @Schema(description = "UI配置：badge/图片类参数(image_upload)等")
    private Map<String, Object> uiConfig;

    @Schema(description = "规则参数：模板 ui_schema 收集的非图片参数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "规则参数 不能为空")
    private Map<String, Object> ruleConfig;

    @Schema(description = "奖励阶梯列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "奖励阶梯 至少配置一级")
    @Valid
    private List<TaskConfigWizardPrizeItemForm> prizeMappingList;
}
