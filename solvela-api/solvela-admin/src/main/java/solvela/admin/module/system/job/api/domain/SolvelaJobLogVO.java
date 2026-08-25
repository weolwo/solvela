package solvela.admin.module.system.job.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.base.common.json.serializer.enumeration.EnumSerialize;
import solvela.base.common.swagger.SchemaEnum;
import solvela.admin.module.system.job.constant.SolvelaJobExecuteStatusEnum;

import java.time.LocalDateTime;

/**
 * 定时任务-执行记录 vo
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
@Data
public class SolvelaJobLogVO {

    @Schema(description = "logId")
    private Long logId;

    @Schema(description = "任务id")
    private Integer jobId;

    @Schema(description = "任务名称")
    private String jobName;

    @Schema(description = "执行时的参数快照（不是当前配置）")
    private String paramSnapshot;

    @Schema(description = "触发来源：SCHEDULE/MANUAL")
    private String triggerSource;

    @Schema(description = "本次调度的原定触发时刻")
    private LocalDateTime triggerTime;

    @Schema(description = "同一触发点的第几次尝试，0 为首次")
    private Integer retrySeq;

    @Schema(description = "业务日期")
    private java.time.LocalDate bizDate;

    @Schema(description = "本次是哪条记录的重试")
    private Long retryOfLogId;

    @Schema(description = "调度延迟毫秒 = 实际开始 - 原定触发。持续增长即为扩容信号")
    private Long scheduleDelayMs;

    @SchemaEnum(desc = "执行状态", value = SolvelaJobExecuteStatusEnum.class)
    @EnumSerialize(SolvelaJobExecuteStatusEnum.class)
    private Integer status;

    @Schema(description = "链路追踪 id，可用它去日志系统检索这次执行")
    private String traceId;

    @Schema(description = "开始执行时间")
    private LocalDateTime executeStartTime;

    @Schema(description = "执行时长-毫秒")
    private Long executeTimeMillis;

    @Schema(description = "执行结果摘要：执行器返回的人话")
    private String resultSummary;

    @Schema(description = "失败时的异常堆栈（已截断）")
    private String errorDetail;

    @Schema(description = "执行结束时间")
    private LocalDateTime executeEndTime;

    @Schema(description = "ip")
    private String ip;

    @Schema(description = "进程id")
    private String processId;

    @Schema(description = "程序目录")
    private String programPath;

    private String createName;

    private LocalDateTime createTime;
}
