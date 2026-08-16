package sa.base.module.support.job.repository.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 定时任务 执行记录 实体类
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
@Data
@TableName("t_smart_job_log")
public class SmartJobLogEntity {

    @TableId(type = IdType.AUTO)
    private Long logId;

    private Integer jobId;

    private String jobName;

    /**
     * 环境标识：冗余列，避免日志表扫描每秒 join {@code t_smart_job}
     */
    private String appEnv;

    /**
     * 链路追踪 id，与 web 请求共用 logback 的 {@code %X{traceId}}
     */
    private String traceId;

    /**
     * 触发来源：SCHEDULE / MANUAL。
     *
     * <p>🔴 没有 RETRY —— 重试<b>继承</b>原记录的来源，靠 {@link #retrySeq} 区分
     */
    private String triggerSource;

    /**
     * 🔴 本次调度的<b>原定</b>触发时刻（不是执行时刻）。
     *
     * <p>它是 {@code uk_job_trigger} 的组成部分，也是 {@link #bizDate} 的计算基准 ——
     * 用 {@code now()} 算的话，misfire 补跑会处理错日期，而补跑恰恰是 bizDate 存在的意义
     */
    private LocalDateTime triggerTime;

    /**
     * 同一触发点的第几次尝试，0 为首次。重试是<b>新记录</b>而不是改旧记录，靠这一列区分
     */
    private Integer retrySeq;

    /**
     * 业务日期：正常调度 = 触发日 + bizDateOffset，重跑时可指定历史日期
     */
    private LocalDate bizDate;

    /**
     * 🔴 何时该被扫描线程捞起执行。手动触发 = now，重试 = now + interval。
     *
     * <p><b>只有 PENDING 记录才有值，其余状态恒 NULL</b> ——
     * FAIL 记录若还带着 fireTime，下一秒就会被再次扫到，变成无限重试。
     * 这是重试状态机能成立的关键约束。
     */
    private LocalDateTime fireTime;

    /**
     * 本次是哪条记录的重试
     */
    private Long retryOfLogId;

    /**
     * 执行时的参数<b>快照</b>（不是当前配置）：重试与一键重跑都复现这一份。
     *
     * <p>叫 snapshot 不是洁癖 —— 叫 param 会诱导人在重试时重读当前配置，
     * 而配置可能早已被人改过
     */
    private String paramSnapshot;

    /**
     * 执行状态。
     *
     * <p>🔴 取代原来的布尔 {@code successFlag} —— 布尔表达不了「执行中」，
     * 所以原实现只能在执行前先写一条「成功」，进程一崩就永久留下假记录。
     *
     * @see sa.base.module.support.job.constant.SmartJobExecuteStatusEnum
     */
    private Integer status;

    private LocalDateTime executeStartTime;

    private Long executeTimeMillis;

    private LocalDateTime executeEndTime;

    /**
     * 🔴 调度延迟 = 实际开始 - 原定触发。
     *
     * <p>背压解决的是「满了怎么办」，这个数回答的是「什么时候该扩容」——
     * 持续增长就是容量不足的信号，不该等到 MISFIRE 告警响了才发现
     */
    private Long scheduleDelayMs;

    /**
     * 执行结果摘要：执行器返回的人话，给运营看
     */
    private String resultSummary;

    /**
     * 失败时的异常堆栈（已截断）。与摘要分列 ——
     * 两者的长度诉求本就不同，混在一列才不得不把堆栈截到 1800
     */
    private String errorDetail;

    private String ip;

    private String processId;

    private String programPath;

    private String createName;

    private LocalDateTime createTime;
}
