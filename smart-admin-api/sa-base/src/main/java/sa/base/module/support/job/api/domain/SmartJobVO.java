package sa.base.module.support.job.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import sa.base.common.json.serializer.enumeration.EnumSerialize;
import sa.base.common.swagger.SchemaEnum;
import sa.base.module.support.job.constant.SmartJobTriggerTypeEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务 vo
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
@Data
public class SmartJobVO {

    @Schema(description = "任务id")
    private Integer jobId;

    @Schema(description = "任务编码：10位大写字母+数字，全局唯一")
    private String jobCode;

    @Schema(description = "任务名称")
    private String jobName;

    @Schema(description = "执行器名称")
    private String handlerName;

    @Schema(description = "执行器中文标题，来自 @SmartJobHandler#title，代码里查不到时为空")
    private String handlerTitle;

    @Schema(description = "🔴 执行器在代码中不存在：该任务永远不会被执行，需要在列表上标红")
    private Boolean handlerMissingFlag;

    @SchemaEnum(desc = "触发类型", value = SmartJobTriggerTypeEnum.class)
    @EnumSerialize(SmartJobTriggerTypeEnum.class)
    private String triggerType;

    @Schema(description = "触发配置")
    private String triggerValue;

    @Schema(description = "定时任务参数|可选")
    private String param;

    @Schema(description = "是否启用")
    private Boolean enabledFlag;

    @Schema(description = "分组")
    private String jobGroup;

    @Schema(description = "执行车道：FAST/SLOW，来自执行器声明")
    private String lane;

    @Schema(description = "预设档位")
    private String presetCode;

    @Schema(description = "下次触发时间：库里的真值，不是前端估算")
    private LocalDateTime nextTriggerTime;

    @Schema(description = "上次触发时间")
    private LocalDateTime prevTriggerTime;

    @Schema(description = "打散秒数")
    private Integer jitterSeconds;

    @Schema(description = "超时秒数")
    private Integer timeoutSeconds;

    @Schema(description = "重试次数")
    private Integer retryTimes;

    @Schema(description = "错过调度策略")
    private String misfireStrategy;

    @Schema(description = "阻塞策略")
    private String blockStrategy;

    @Schema(description = "连续失败次数")
    private Integer continuousFailCount;

    @Schema(description = "一次性任务是否已终结")
    private Boolean terminalFlag;

    @Schema(description = "来源：MANUAL 人工创建 / SYSTEM 系统或向导生成")
    private String source;

    @Schema(description = "最后一执行时间")
    private LocalDateTime lastExecuteTime;

    @Schema(description = "最后一次执行记录id")
    private Long lastExecuteLogId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "排序")
    private Integer sort;

    private String updateName;

    private LocalDateTime updateTime;

    private LocalDateTime createTime;

    @Schema(description = "上次执行记录")
    private SmartJobLogVO lastJobLog;

    @Schema(description = "未来N次任务执行时间")
    private List<LocalDateTime> nextJobExecuteTimeList;
}
