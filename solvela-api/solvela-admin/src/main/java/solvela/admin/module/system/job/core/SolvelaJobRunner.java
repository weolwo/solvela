package solvela.admin.module.system.job.core;

import solvela.base.module.support.jobspi.core.SolvelaJobContext;
import solvela.base.module.support.jobspi.core.SolvelaJobCancelledException;
import solvela.base.module.support.jobspi.core.SolvelaJob;
import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.job.constant.SolvelaJobExecuteStatusEnum;
import solvela.base.module.support.jobspi.constant.SolvelaJobLaneEnum;
import solvela.admin.module.system.job.repository.SolvelaJobDao;
import solvela.admin.module.system.job.repository.SolvelaJobLogDao;
import solvela.admin.module.system.job.repository.SolvelaJobRepository;
import solvela.admin.module.system.job.repository.domain.SolvelaJobEntity;
import solvela.admin.module.system.job.repository.domain.SolvelaJobLogEntity;
import solvela.admin.module.system.job.log.SolvelaJobLogContext;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务执行器：跑业务、落状态、按需产出重试记录。
 *
 * <p>它不做调度决策 —— 那些在 {@link SolvelaJobScanner} 里。
 * 进到这里时任务已经被抢到，本类只关心「跑起来、如实记录、失败了安排重试」。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SolvelaJobRunner {

    private static final String TRACE_ID = "traceId";

    /**
     * 异常堆栈落库前的截断长度。
     *
     * <p>见铁律：往列里塞超长文本会在<b>异常分支上二次抛异常</b>，
     * 把原本要记录的失败原因彻底吞掉 —— 于是你只知道它失败了，永远不知道为什么
     */
    private static final int ERROR_DETAIL_MAX_LENGTH = 1800;

    /**
     * 摘要列宽 512，留一点余量
     */
    private static final int RESULT_SUMMARY_MAX_LENGTH = 500;

    private final SolvelaJobRepository jobRepository;

    private final SolvelaJobLaneExecutePool executePool;

    public SolvelaJobRunner(SolvelaJobRepository jobRepository, SolvelaJobLaneExecutePool executePool) {
        this.jobRepository = jobRepository;
        this.executePool = executePool;
    }

    /**
     * 投递执行。调用方已完成抢占与日志插入，这里只负责跑。
     *
     * @return false 表示车道已满未能投递，调用方需如实落 BLOCKED
     */
    public boolean submit(SolvelaJobEntity job, SolvelaJobLogEntity logEntity, SolvelaJobHandlerMeta handler) {
        SolvelaJobLaneEnum lane = handler.lane();
        int timeoutSeconds = this.resolveTimeoutSeconds(job, handler);
        return executePool.submit(lane, job.getJobName(), logEntity.getLogId(), timeoutSeconds,
                timedOut -> this.run(job, logEntity, handler, timedOut, timeoutSeconds));
    }

    /**
     * 超时取值：任务配置优先，未配则取执行器声明值。
     *
     * <p>🔴 FAST 车道有硬上限：声明 FAST 就等于承诺「我 30 秒内跑完」，
     * 框架把超时压到上限并按时中断 —— <b>声明必须被执行强制</b>，
     * 否则一个「声称 FAST 实则跑 5 分钟」的执行器照样能毒死快车道，隔离只存在于注释里。
     */
    private int resolveTimeoutSeconds(SolvelaJobEntity job, SolvelaJobHandlerMeta handler) {
        int configured = null == job.getTimeoutSeconds() ? 0 : job.getTimeoutSeconds();
        int timeout = configured > 0 ? configured : handler.defaultTimeoutSeconds();
        if (handler.lane() == SolvelaJobLaneEnum.FAST) {
            int cap = SolvelaJobLaneEnum.FAST_MAX_TIMEOUT_SECONDS;
            return timeout <= 0 ? cap : Math.min(timeout, cap);
        }
        return timeout;
    }

    /**
     * 在执行池线程上跑。
     */
    private void run(SolvelaJobEntity job, SolvelaJobLogEntity logEntity, SolvelaJobHandlerMeta handler,
                     AtomicBoolean timedOut, int timeoutSeconds) {
        MDC.put(TRACE_ID, logEntity.getTraceId());
        // 开启本次执行的日志采集：Appender 据此判断「这条日志属于哪次执行」
        SolvelaJobLogContext.bind(logEntity.getLogId());
        long startNanos = System.nanoTime();

        SolvelaJobExecuteStatusEnum status;
        String resultSummary = null;
        String errorDetail = null;
        try {
            SolvelaJobContext ctx = new SolvelaJobContext(
                    job.getJobCode(), job.getJobName(), job.getHandlerName(),
                    logEntity.getParamSnapshot(), logEntity.getBizDate(),
                    logEntity.getTriggerTime(), logEntity.getExecuteStartTime(),
                    logEntity.getTraceId(), logEntity.getLogId(),
                    null == logEntity.getRetrySeq() ? 0 : logEntity.getRetrySeq(),
                    0, 1, timedOut::get);
            resultSummary = truncate(handler.instance().execute(ctx), RESULT_SUMMARY_MAX_LENGTH);
            status = SolvelaJobExecuteStatusEnum.SUCCESS;
        } catch (SolvelaJobCancelledException e) {
            // 执行器主动响应了中断 —— 这是它该做的，不是 bug
            status = SolvelaJobExecuteStatusEnum.TIMEOUT;
            errorDetail = "执行超时被中断，超时阈值 " + timeoutSeconds + " 秒";
            log.error("==== SolvelaJob ==== 超时中断：{}", job.getJobName());
        } catch (Throwable t) {
            // 🔴 Throwable 而不是 Exception：OOM / NoSuchMethodError 属于 Error，
            //    漏掉它们会让线程直接暴毙，状态永久停在 RUNNING，
            //    还会让阻塞判断永久误判为「上一次还在跑」——这个任务从此再也不会被执行
            if (timedOut.get()) {
                status = SolvelaJobExecuteStatusEnum.TIMEOUT;
                errorDetail = "执行超时被中断，超时阈值 " + timeoutSeconds + " 秒";
                log.error("==== SolvelaJob ==== 超时中断：{}", job.getJobName());
            } else {
                status = SolvelaJobExecuteStatusEnum.FAIL;
                errorDetail = stackTraceToString(t, ERROR_DETAIL_MAX_LENGTH);
                log.error("==== SolvelaJob ==== 执行失败：{}", job.getJobName(), t);
            }
        }
        long costMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        // 🔴 落库前先清掉中断标记：被 cancel(true) 打断的线程带着中断状态，
        //    部分 JDBC 驱动会直接拒绝执行 —— 那就又回到「状态永远停在 RUNNING」了
        boolean wasInterrupted = Thread.interrupted();
        try {
            this.finish(job, logEntity, handler, status, resultSummary, errorDetail, costMillis);
        } catch (Throwable t) {
            log.error("==== SolvelaJob ==== 执行记录落库失败，该条将由僵尸扫描兜底：logId={}",
                    logEntity.getLogId(), t);
        } finally {
            // 超时中断是「这次执行结束了」，不必把中断状态还给线程池；
            // 停机中断则要还回去，否则池的优雅停机会被吞掉
            if (wasInterrupted && !timedOut.get()) {
                Thread.currentThread().interrupt();
            }
            // 🔴 必须清：池线程复用，不清会把下一个任务的日志收进上一个任务的缓冲区
            SolvelaJobLogContext.clear();
            MDC.remove(TRACE_ID);
        }
    }

    /**
     * 落终态 + 按需产出重试记录。
     *
     * <p>🔴 <b>重试是一条全新的 PENDING 记录，绝不改动失败记录本身。</b>
     * 改动的话历史现场就没了（那次失败的堆栈、耗时、节点全部被覆盖），
     * 而且 {@code uk_job_trigger} 里的 {@code retry_seq} 会恒为 0 ——
     * <b>那一列存在的唯一意义就是「重试是新行」</b>，复用旧行等于让唯一索引对重试路径完全失效。
     *
     * <p>三条必须同时成立的约束（缺一条就出问题），实现见 {@link SolvelaJobRepository#finishAndScheduleRetry}：
     * <ol>
     *   <li>失败记录的 {@code fire_time} 留 NULL —— 否则它下一秒会被再次扫到，<b>变成无限重试</b>；</li>
     *   <li>置 FAIL 与插 PENDING 必须<b>同一个事务</b> —— 否则重试记录静默丢失，而日志上一切正常；</li>
     *   <li>{@code trigger_time} / {@code param_snapshot} / {@code biz_date} <b>原样继承</b> ——
     *       重试的是「当时那一次」，配置可能已经被人改过。</li>
     * </ol>
     */
    private void finish(SolvelaJobEntity job, SolvelaJobLogEntity logEntity, SolvelaJobHandlerMeta handler,
                        SolvelaJobExecuteStatusEnum status, String resultSummary, String errorDetail,
                        long costMillis) {
        LocalDateTime endTime = logEntity.getExecuteStartTime().plusNanos(costMillis * 1_000_000L);

        SolvelaJobLogEntity update = new SolvelaJobLogEntity();
        update.setLogId(logEntity.getLogId());
        update.setStatus(status.getValue());
        update.setExecuteEndTime(endTime);
        update.setExecuteTimeMillis(costMillis);
        update.setResultSummary(resultSummary);
        update.setErrorDetail(errorDetail);
        // fire_time 保持 NULL（抢占时已置空），终态记录不该再被扫描线程捞到

        SolvelaJobLogEntity retryEntity = this.buildRetryEntity(job, logEntity, handler, status);
        jobRepository.finishAndScheduleRetry(update, retryEntity, job.getJobId(),
                status == SolvelaJobExecuteStatusEnum.SUCCESS ? 0 : 1, logEntity.getLogId());

        log.info("==== SolvelaJob ==== 执行完毕 job={} status={} cost={}ms delay={}ms{}",
                job.getJobName(), status.getDesc(), costMillis,
                logEntity.getScheduleDelayMs(),
                null == retryEntity ? "" : " → 已安排第 " + retryEntity.getRetrySeq() + " 次重试");
    }

    /**
     * 构造重试记录；不需要重试时返回 null。
     */
    private SolvelaJobLogEntity buildRetryEntity(SolvelaJobEntity job, SolvelaJobLogEntity logEntity,
                                                 SolvelaJobHandlerMeta handler, SolvelaJobExecuteStatusEnum status) {
        if (status == SolvelaJobExecuteStatusEnum.SUCCESS) {
            return null;
        }
        int retryTimes = null == job.getRetryTimes() ? 0 : job.getRetryTimes();
        if (retryTimes <= 0) {
            return null;
        }
        // 🔴 不幂等的执行器不许重试：自动重试等于把一次失败变成两次副作用。
        //    保存时已校验过，这里再挡一道 —— 配置可能是从别的环境同步过来的
        if (!handler.idempotent()) {
            log.warn("==== SolvelaJob ==== 执行器未声明幂等，跳过重试：job={} handler={}",
                    job.getJobName(), job.getHandlerName());
            return null;
        }
        int currentSeq = null == logEntity.getRetrySeq() ? 0 : logEntity.getRetrySeq();
        if (currentSeq >= retryTimes) {
            log.error("==== SolvelaJob ==== 重试次数已耗尽（{}/{}）：{}", currentSeq, retryTimes, job.getJobName());
            return null;
        }

        int interval = null == job.getRetryInterval() ? 30 : job.getRetryInterval();
        SolvelaJobLogEntity retry = new SolvelaJobLogEntity();
        retry.setJobId(logEntity.getJobId());
        retry.setJobName(logEntity.getJobName());
        retry.setAppEnv(logEntity.getAppEnv());
        // traceId 不继承：重试是一次新的执行，应当有自己的链路
        retry.setTraceId(null);
        // 🔴 以下四项必须原样继承，理由见 finish() 的 javadoc
        retry.setTriggerSource(logEntity.getTriggerSource());
        retry.setTriggerTime(logEntity.getTriggerTime());
        retry.setParamSnapshot(logEntity.getParamSnapshot());
        retry.setBizDate(logEntity.getBizDate());
        retry.setRetrySeq(currentSeq + 1);
        retry.setRetryOfLogId(logEntity.getLogId());
        retry.setStatus(SolvelaJobExecuteStatusEnum.PENDING.getValue());
        // 🔴 只有 PENDING 记录带 fire_time
        retry.setFireTime(logEntity.getExecuteStartTime().plusSeconds(interval));
        retry.setCreateName(logEntity.getCreateName());
        // ⚠️ execute_start_time / ip / process_id / program_path 刻意留 NULL ——
        //    重试记录还没开始执行，也不知道会落到哪个节点。这几列 v3.59.0 起可空。
        //    （在那之前它们是 NOT NULL，插入直接报
        //     `Field 'execute_start_time' doesn't have a default value`，
        //     而这个插入与「置 FAIL」在同一事务里 —— 一起回滚，
        //     记录就永久卡在 RUNNING 了。2026-08-12 实测踩到。）
        return retry;
    }

    private static String truncate(String text, int maxLength) {
        if (null == text) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private static String stackTraceToString(Throwable throwable, int maxLength) {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
        }
        return truncate(stringWriter.toString(), maxLength);
    }
}
