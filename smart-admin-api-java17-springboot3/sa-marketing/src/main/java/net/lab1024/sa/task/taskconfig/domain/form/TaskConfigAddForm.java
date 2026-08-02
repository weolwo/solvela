package net.lab1024.sa.task.taskconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务配置表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:55:10
 * @Copyright weolwo
 */

@Data
public class TaskConfigAddForm {

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    @Schema(description = "租户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户ID 不能为空")
    private String tenantId;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务名称 不能为空")
    private String taskName;

    @Schema(description = "模板Code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板Code 不能为空")
    private String templateCode;

    @Schema(description = "触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "触发事件：ORDER_PAID(支付), MEMBER_REGISTER(注册), DAILY_SIGN(签到), PAGE_VIEW(浏览), CUSTOM(自定义) 不能为空")
    private String triggerEvent;

    @Schema(description = "任务分组：NEWBIE(新手), DAILY(日常), PROMO(大促), VIP(会员专属)")
    private String taskGroup;

    @Schema(description = "目标人群：ALL(全部), NEW_MEMBER(新会员), OLD_MEMBER(老会员)")
    private String targetAudience;

    @Schema(description = "参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "参与频次：ONCE(终身一次), DAILY(每日重复), WEEKLY(每周重复), UNLIMITED(无限制) 不能为空")
    private String limitType;

    @Schema(description = "配合频次类型，限制次数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "配合频次类型，限制次数 不能为空")
    private Integer limitCount;

    @Schema(description = "规则配置", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规则配置 不能为空")
    private String ruleConfig;

    @Schema(description = "排序权重，越大越靠前")
    private Integer sortWeight;

    @Schema(description = "跳转地址")
    private String actionUrl;

    @Schema(description = "展示UI(图标/角标等)")
    private String uiConfig;

    @Schema(description = "任务状态 1-待生效, 2-生效中, 3-已下线")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

}