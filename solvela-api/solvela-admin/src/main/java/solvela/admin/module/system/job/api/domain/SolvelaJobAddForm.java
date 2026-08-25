package solvela.admin.module.system.job.api.domain;

import solvela.base.module.support.jobspi.core.SolvelaJobHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import solvela.base.common.swagger.SchemaEnum;
import solvela.base.common.validator.enumeration.CheckEnum;
import solvela.admin.module.system.job.constant.SolvelaJobTriggerTypeEnum;
import org.hibernate.validator.constraints.Length;

/**
 * 定时任务 添加
 *
 * @author huke
 * @date 2024/12/19 19:30
 */
@Data
public class SolvelaJobAddForm {

    @Schema(description = "任务名称")
    @NotBlank(message = "任务名称不能为空")
    @Length(max = 100, message = "任务名称最多100字符")
    private String jobName;

    @Schema(description = "执行器名称，对应 @SolvelaJobHandler 的 name")
    @NotBlank(message = "执行器不能为空")
    @Length(max = 64, message = "执行器名称最多64字符")
    private String handlerName;

    @SchemaEnum(desc = "触发类型", value = SolvelaJobTriggerTypeEnum.class)
    @CheckEnum(value = SolvelaJobTriggerTypeEnum.class, required = true, message = "触发类型错误")
    private String triggerType;

    @Schema(description = "触发配置")
    @NotBlank(message = "触发配置不能为空")
    @Length(max = 100, message = "触发配置最多100字符")
    private String triggerValue;

    @Schema(description = "定时任务参数|可选")
    @Length(max = 1000, message = "定时任务参数最多1000字符")
    private String param;

    @Schema(description = "是否开启")
    @NotNull(message = "是否开启不能为空")
    private Boolean enabledFlag;

    @Schema(description = "备注")
    @Length(max = 250, message = "任务备注最多250字符")
    private String remark;

    @NotNull(message = "排序不能为空")
    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "预设档位：LIGHT/NORMAL/HEAVY/CUSTOM。不填按 NORMAL")
    private String presetCode;

    // ==================== 以下仅 CUSTOM 档位生效 ====================
    // 🔴 非 CUSTOM 时这些字段一律被档位覆盖，不是「以表单为准」——
    //    否则运营选了「轻量高频」却又手填了 30 分钟超时，两者矛盾时谁说了算就没有答案了

    @Schema(description = "超时秒数，0=取执行器声明值（仅 CUSTOM 生效）")
    private Integer timeoutSeconds;

    @Schema(description = "失败重试次数，仅幂等执行器可 >0（仅 CUSTOM 生效）")
    private Integer retryTimes;

    @Schema(description = "重试间隔秒数（仅 CUSTOM 生效）")
    private Integer retryInterval;

    @Schema(description = "错过调度策略：SKIP/FIRE_ONCE（仅 CUSTOM 生效）")
    private String misfireStrategy;

    @Schema(description = "错过调度判定阈值秒数（仅 CUSTOM 生效）")
    private Integer misfireThresholdSec;

    @Schema(description = "阻塞策略：DISCARD/SERIAL/OVERRIDE（仅 CUSTOM 生效）")
    private String blockStrategy;

    @Schema(description = "打散秒数，ONE_TIME 强制 0（仅 CUSTOM 生效）")
    private Integer jitterSeconds;

    @Schema(description = "分组")
    private String jobGroup;

    @Schema(description = "告警接收人，多个逗号分隔")
    private String alarmReceiver;

    @Schema(hidden = true)
    private String updateName;
}
