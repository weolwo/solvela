package solvela.admin.module.system.job.core;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import solvela.base.util.SolvelaIpUtil;
import solvela.admin.module.system.job.config.SolvelaJobConfig;
import solvela.admin.module.system.job.constant.SolvelaJobBlockStrategyEnum;
import solvela.admin.module.system.job.constant.SolvelaJobExecuteStatusEnum;
import solvela.admin.module.system.job.constant.SolvelaJobMisfireStrategyEnum;
import solvela.admin.module.system.job.constant.SolvelaJobTriggerSourceEnum;
import solvela.admin.module.system.job.constant.SolvelaJobTriggerTypeEnum;
import solvela.admin.module.system.job.constant.SolvelaJobUtil;
import solvela.admin.module.system.job.repository.SolvelaJobRepository;
import solvela.admin.module.system.job.repository.domain.SolvelaJobEntity;
import solvela.admin.module.system.job.repository.domain.SolvelaJobLogEntity;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 🔴 抢占式调度扫描器 —— 第二档的核心。
 *
 * <p>它取代了原来「每个节点各自维护内存 Trigger + 触发瞬间抢 Redis 锁」的模型。
 * 换掉的理由不是内存模型不好用，而是<b>调度的真源放错了地方</b>：
 * <ul>
 *   <li>时钟散在每个节点的 JVM 上 —— 漂移会直接表现为重跑或漏跑（撞铁律 9/10）；</li>
 *   <li>停机期间漏掉的调度<b>无痕消失</b>，运营永远不知道昨晚那次统计没跑；</li>
 *   <li>配置变更只能靠 pub/sub 通知 + 全表轮询兜底，而 pub/sub 丢消息就是丢了。</li>
 * </ul>
 * {@code next_trigger_time} 落库之后，这三件事一起解决。
 *
 * <p><b>每轮扫描分两步，抢占的对象不同：</b>
 * <ol>
 *   <li>扫 {@code t_solvela_job}，抢 {@code trigger_version}（定时调度）；</li>
 *   <li>扫 {@code t_solvela_job_log}，抢日志行的 {@code status}（手动触发 + 失败重试）。</li>
 * </ol>
 * 🔴 第二步<b>绝不能</b>去抢 {@code trigger_version}：那会连带改写 {@code next_trigger_time}，
 * <b>扰乱正常的 cron 节奏</b> —— 运营点一次「立即执行」，下一次定时就漂了。
 *
 * <p><b>单条任务的四步时序不可调换：</b>
 * <pre>
 * ① 判池水位（节点级）→ 满则跳过，不抢占，留给别的节点
 * ② 抢占（推进 next_trigger_time）
 * ③ 判阻塞（集群级）→ 阻塞则写 BLOCKED 日志，终止
 * ④ 插 RUNNING 日志（唯一索引兜底）→ 投递
 * </pre>
 * ①和③看似矛盾（都是「跑不了」却反着处理），规则其实只有一条：
 * <b>节点级的判据跳过，集群级的判据抢占。</b>
 * 把③放到②前面的后果是：一个 1 分钟触发、实际跑 5 分钟的任务，
 * {@code next_trigger_time} 永远停在过去，<b>时间轮再也不转</b>，
 * 而且会顺带触发「超期未触发」告警，把正常的长任务报成「调度器挂了」。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SolvelaJobScanner {

    private final SolvelaJobConfig jobConfig;

    private final SolvelaJobRepository jobRepository;

    private final SolvelaJobHandlerRegistry handlerRegistry;

    private final SolvelaJobRunner jobRunner;

    private final SolvelaJobLaneExecutePool executePool;

    private final ScheduledExecutorService scanExecutor;

    private final String nodeIp;

    public SolvelaJobScanner(SolvelaJobConfig jobConfig,
                             SolvelaJobRepository jobRepository,
                             SolvelaJobHandlerRegistry handlerRegistry,
                             SolvelaJobRunner jobRunner,
                             SolvelaJobLaneExecutePool executePool) {
        this.jobConfig = jobConfig;
        this.jobRepository = jobRepository;
        this.handlerRegistry = handlerRegistry;
        this.jobRunner = jobRunner;
        this.executePool = executePool;
        this.nodeIp = SolvelaIpUtil.getLocalFirstIp();

        ThreadFactory factory = Thread.ofPlatform().name("solvela-job-scanner-", 0).factory();
        this.scanExecutor = Executors.newSingleThreadScheduledExecutor(factory);
        this.scanExecutor.scheduleWithFixedDelay(this::scanSafely,
                jobConfig.getInitDelay(), jobConfig.getScanIntervalSeconds(), TimeUnit.SECONDS);

        log.info("==== SolvelaJob ==== 扫描器启动 env={} 间隔={}s 批量={}",
                jobConfig.getEnv(), jobConfig.getScanIntervalSeconds(), jobConfig.getScanBatchSize());
    }

    /**
     * 🔴 异常绝不能逃出扫描线程。
     *
     * <p>{@code scheduleWithFixedDelay} 的语义是「任务抛异常则<b>后续不再执行</b>」——
     * 一次没接住的异常会让整个调度器<b>永久停摆且毫无迹象</b>。
     * 用 Throwable 而不是 Exception，因为 Error 同样会终止它。
     */
    private void scanSafely() {
        try {
            this.scan();
        } catch (Throwable t) {
            log.error("==== SolvelaJob ==== 扫描异常（本轮跳过，下轮继续）", t);
        }
    }

    private void scan() {
        // 🔴 一轮扫描只取一次数据库时钟，后续所有判断都基于它。
        //    每处各取一次会引入轮内不一致：同一轮里 A 判断认为没到点、B 判断认为已超期
        LocalDateTime dbNow = jobRepository.getJobDao().selectDbNow();
        if (null == dbNow) {
            log.error("==== SolvelaJob ==== 取数据库时钟失败，本轮跳过");
            return;
        }
        this.scanScheduledJob(dbNow);
        this.scanPendingLog(dbNow);
    }

    // ==================== 第一步：定时任务 ====================

    private void scanScheduledJob(LocalDateTime dbNow) {
        // 🔴 只捞「已经到点」的，绝不预读。
        //    曾经这里是 dbNow + scanPreloadSeconds（5 秒预读窗口），本意是避免边界抖动，
        //    实测（2026-08-12）造成三个连锁问题：
        //      ① 任务真的**提前 5 秒执行** —— 对 ONE_TIME 就是「活动提前 5 秒开」，
        //         正是 jitter 那条注释里说绝不能干的事；
        //      ② schedule_delay_ms 变成 -5000，扩容信号这个指标直接失效；
        //      ③ **把 jitter 的效果整个抵消掉** —— 打散让两个任务错到 :05 和 :06，
        //         预读又把它们一起拽回 :00 执行，等于没散。
        //    扫描间隔本就是 1 秒，不预读的最大迟到也就 1 秒，远比提前执行安全。
        List<SolvelaJobEntity> dueList = jobRepository.getJobDao()
                .selectDueJobList(jobConfig.getEnv(), dbNow, jobConfig.getScanBatchSize());
        for (SolvelaJobEntity job : dueList) {
            try {
                this.handleDueJob(job, dbNow);
            } catch (Throwable t) {
                // 单条任务出错不影响同轮的其它任务
                log.error("==== SolvelaJob ==== 处理到期任务异常：{}", job.getJobName(), t);
            }
        }
    }

    private void handleDueJob(SolvelaJobEntity job, LocalDateTime dbNow) {
        Optional<SolvelaJobHandlerMeta> handlerOpt = handlerRegistry.getHandler(job.getHandlerName());
        if (handlerOpt.isEmpty()) {
            // 标记失联并让它退出调度：不标记的话每秒都会被扫到、每秒报一次错，刷屏还查不出所以然
            log.error("==== SolvelaJob ==== 🔴 handler 在代码中不存在，任务已停止调度：job={} handler={}",
                    job.getJobName(), job.getHandlerName());
            jobRepository.getJobDao().updateHandlerMissing(job.getJobId(), true);
            return;
        }
        SolvelaJobHandlerMeta handler = handlerOpt.get();

        // ① 节点级判据：本车道满了就跳过，不抢占。
        //    任务原封不动留在库里，下一轮任何一个有空位的节点自然会接走 ——
        //    这样「本该被别人执行的任务被就地误杀」根本不会发生
        if (!executePool.hasCapacity(handler.lane())) {
            log.debug("==== SolvelaJob ==== {} 车道已满，跳过（保留待抢）：{}",
                    handler.lane().getValue(), job.getJobName());
            return;
        }

        LocalDateTime triggerTime = job.getNextTriggerTime();
        boolean misfired = this.isMisfired(job, triggerTime, dbNow);
        SolvelaJobMisfireStrategyEnum misfireStrategy = SolvelaJobMisfireStrategyEnum.resolve(job.getMisfireStrategy());

        // ② 抢占：推进时间。无论后面是否真的执行，时间轮都必须往前走
        if (!this.preempt(job, dbNow)) {
            return;
        }

        // 错过且策略为跳过：只记录，不执行。
        // 🔴 「跳过」必须留痕 ——「跳过了」和「从来没触发过」在运营那儿是两件事
        if (misfired && misfireStrategy == SolvelaJobMisfireStrategyEnum.SKIP) {
            log.warn("==== SolvelaJob ==== 错过调度已跳过：job={} 原定={} 现在={}",
                    job.getJobName(), triggerTime, dbNow);
            this.saveTerminalLog(job, triggerTime, dbNow, SolvelaJobExecuteStatusEnum.MISFIRE,
                    "错过调度窗口，按 SKIP 策略跳过。原定触发 " + triggerTime);
            return;
        }

        this.dispatch(job, handler, triggerTime, dbNow, SolvelaJobTriggerSourceEnum.SCHEDULE, 0,
                job.getParam(), null, null, null);
    }

    /**
     * 是否错过调度窗口。
     *
     * <p>🔴 阈值取任务自己的 {@code misfire_threshold_sec}（随预设档位联动），
     * <b>不能全局写死</b>。原因是它与背压跳过直接冲突：慢车道满时任务会被跳过、
     * 留在库里排队，阈值若固定 60 秒，排队超过一分钟就会被误判成 misfire ——
     * 策略是 SKIP 的话就<b>被静默丢弃</b>，完全违背背压排队的初衷。
     *
     * <p>已知残留局限：这只是缓解。判据仍分不清「系统没在跑」和「系统在跑但没排上」，
     * 慢车道若持续堵塞超过阈值依然会误判。<b>这是可接受的退化</b> ——
     * 那种情况下真正的问题是容量不足，正确动作是扩容而不是继续调阈值，
     * 而 {@code schedule_delay_ms} 指标就是发现它的手段。
     */
    private boolean isMisfired(SolvelaJobEntity job, LocalDateTime triggerTime, LocalDateTime dbNow) {
        if (null == triggerTime) {
            return false;
        }
        int threshold = null == job.getMisfireThresholdSec() ? 300 : job.getMisfireThresholdSec();
        return Duration.between(triggerTime, dbNow).getSeconds() > threshold;
    }

    /**
     * 抢占执行权。影响行数 = 1 才算抢到。
     */
    private boolean preempt(SolvelaJobEntity job, LocalDateTime dbNow) {
        LocalDateTime nextTime = this.calcNextTriggerTime(job, dbNow);
        int affected = jobRepository.getJobDao().preemptJob(
                job.getJobId(), job.getTriggerVersion(), nextTime, job.getNextTriggerTime());
        if (affected != 1) {
            // 别的节点抢走了，或任务在这几毫秒里被停用/删除 —— 都不是错误，安静退出
            return false;
        }
        // ONE_TIME 执行后就该退出调度。置终态放在抢占成功之后，
        // 保证「只有真正拿到执行权的那个节点」去改终态
        if (SolvelaJobTriggerTypeEnum.ONE_TIME.equalsValue(job.getTriggerType())) {
            jobRepository.getJobDao().markTerminal(job.getJobId());
        }
        return true;
    }

    /**
     * 算下次触发时间。
     *
     * <p>🔴 基准是数据库时钟 {@code dbNow} 而不是上次的 {@code next_trigger_time}：
     * 停机很久之后重启，按旧值往前推会在循环里算出一大堆过去的时间点。
     */
    private LocalDateTime calcNextTriggerTime(SolvelaJobEntity job, LocalDateTime dbNow) {
        if (SolvelaJobTriggerTypeEnum.ONE_TIME.equalsValue(job.getTriggerType())) {
            // 一次性任务没有下次；置 NULL 由 markTerminal 完成，这里给个远期值只是占位，
            // 实际会被 markTerminal 覆盖成 NULL
            return null;
        }
        LocalDateTime next = SolvelaJobUtil.nextTriggerTime(job.getTriggerType(), job.getTriggerValue(), dbNow);
        if (null == next) {
            log.error("==== SolvelaJob ==== 触发配置无法解析，任务将停止调度：job={} value={}",
                    job.getJobName(), job.getTriggerValue());
            return null;
        }
        int jitter = null == job.getJitterSeconds() ? 0 : job.getJitterSeconds();
        return SolvelaJobUtil.applyJitter(next, job.getJobId(), jitter);
    }

    // ==================== 第二步：手动触发与重试 ====================

    /**
     * 🔴 手动与重试共用同一个状态机：都是 {@code status = PENDING} 的记录，
     * 区别只在 {@code fire_time}。所以这里只有一个条件、没有任何分支。
     */
    private void scanPendingLog(LocalDateTime dbNow) {
        List<SolvelaJobLogEntity> pendingList = jobRepository.getJobLogDao()
                .selectPendingList(jobConfig.getEnv(), dbNow, jobConfig.getScanBatchSize());
        for (SolvelaJobLogEntity pending : pendingList) {
            try {
                this.handlePendingLog(pending, dbNow);
            } catch (Throwable t) {
                log.error("==== SolvelaJob ==== 处理待执行记录异常：logId={}", pending.getLogId(), t);
            }
        }
    }

    private void handlePendingLog(SolvelaJobLogEntity pending, LocalDateTime dbNow) {
        SolvelaJobEntity job = jobRepository.getJobDao().selectById(pending.getJobId());
        if (null == job) {
            log.warn("==== SolvelaJob ==== 待执行记录对应的任务已不存在：logId={}", pending.getLogId());
            this.abandonPending(pending, "任务已不存在");
            return;
        }
        // 🔴 这条链路不经过抢占 SQL 的状态防御，必须自己挡一道。
        //    校验口径与主链路刻意不同：enabled_flag 放行 ——
        //    「忽略任务的开启状态，立即执行一次」是运营调试未启用任务的既有能力，要保留；
        //    但已删除、以及 handler 失联的任务必须挡住
        if (Boolean.TRUE.equals(job.getDeletedFlag())) {
            this.abandonPending(pending, "任务已删除");
            return;
        }
        Optional<SolvelaJobHandlerMeta> handlerOpt = handlerRegistry.getHandler(job.getHandlerName());
        if (handlerOpt.isEmpty()) {
            this.abandonPending(pending, "handler 在代码中不存在：" + job.getHandlerName());
            return;
        }
        SolvelaJobHandlerMeta handler = handlerOpt.get();

        // ① 节点级：车道满了就留着，别的节点会接
        if (!executePool.hasCapacity(handler.lane())) {
            return;
        }
        // ② 抢日志行（不是 trigger_version）
        int affected = jobRepository.getJobLogDao().preemptPendingLog(pending.getLogId(), nodeIp, dbNow);
        if (affected != 1) {
            return;
        }
        if (null == pending.getTraceId()) {
            // 提前生成：BLOCKED 记录也需要 traceId，否则排障时它和同期日志对不上
            pending.setTraceId(newTraceId());
        }
        // ③ 阻塞判定
        // 🔴 排除自己：上一步刚把这条记录抢成 RUNNING，不排除就会数到自己
        if (this.isBlocked(job, handler, dbNow, pending.getLogId())) {
            SolvelaJobLogEntity update = new SolvelaJobLogEntity();
            update.setLogId(pending.getLogId());
            update.setStatus(SolvelaJobExecuteStatusEnum.BLOCKED);
            update.setExecuteEndTime(dbNow);
            update.setExecuteTimeMillis(0L);
            update.setResultSummary("上一次执行尚未结束，按阻塞策略丢弃本次");
            update.setTraceId(pending.getTraceId());
            // 🔴 阻塞记录也要记下「是哪个节点做的判定」。
            //    多节点下这是唯一能区分「跨节点阻塞」与「同节点阻塞」的线索 ——
            //    2026-08-12 实测时正因为这里缺了节点信息，拿到一条 pid 为空的 BLOCKED 记录，
            //    没法证明判定究竟发生在哪一侧。排障时这个区别很关键：
            //    跨节点说明 DB 兜底那层在工作，同节点则可能是 Redis 快判没生效。
            this.fillNodeInfo(update);
            jobRepository.getJobLogDao().updateById(update);
            return;
        }
        // ④ 投递。记录已是 RUNNING，补齐执行期字段后交给 runner
        pending.setStatus(SolvelaJobExecuteStatusEnum.RUNNING);
        pending.setExecuteStartTime(dbNow);
        pending.setScheduleDelayMs(this.calcDelayMillis(pending.getTriggerTime(), dbNow));
        this.fillNodeInfo(pending);
        jobRepository.getJobLogDao().updateById(pending);

        if (!jobRunner.submit(job, pending, handler)) {
            this.abandonPending(pending, "执行池已满，未能投递");
        }
    }

    /**
     * 放弃一条待执行记录，落终态并写明原因（不能静默删）
     */
    private void abandonPending(SolvelaJobLogEntity pending, String reason) {
        SolvelaJobLogEntity update = new SolvelaJobLogEntity();
        update.setLogId(pending.getLogId());
        update.setStatus(SolvelaJobExecuteStatusEnum.BLOCKED);
        update.setFireTime(null);
        update.setResultSummary(reason);
        jobRepository.getJobLogDao().updateById(update);
        log.warn("==== SolvelaJob ==== 待执行记录被放弃：logId={} 原因={}", pending.getLogId(), reason);
    }

    // ==================== 公共：阻塞判定与投递 ====================

    /**
     * 集群级判据：上一次是否还在跑。
     *
     * <p>两层缺一不可 —— 这里用 DB 的 RUNNING 记录作为跨节点的事实来源。
     * 时间下界取 {@code 超时 × 2}，超过这个岁数的 RUNNING 记录一律视为僵尸而不是「还在跑」，
     * 否则一条僵尸记录会让这个任务<b>永久判定为阻塞、再也不执行</b>。
     * 真正的回收由内置任务 {@code _jobZombieScan} 负责。
     */
    private boolean isBlocked(SolvelaJobEntity job, SolvelaJobHandlerMeta handler, LocalDateTime dbNow,
                              Long excludeLogId) {
        SolvelaJobBlockStrategyEnum strategy = SolvelaJobBlockStrategyEnum.resolve(job.getBlockStrategy());
        if (strategy == SolvelaJobBlockStrategyEnum.OVERRIDE) {
            // 中断上一次由超时哨兵负责，这里直接放行
            return false;
        }
        int timeout = null == job.getTimeoutSeconds() || job.getTimeoutSeconds() <= 0
                ? handler.defaultTimeoutSeconds() : job.getTimeoutSeconds();
        LocalDateTime earliest = dbNow.minusSeconds(Math.max(60L, timeout * 2L));
        int running = jobRepository.getJobLogDao().countRunning(job.getJobId(), earliest, excludeLogId);
        if (running > 0) {
            log.info("==== SolvelaJob ==== 上一次尚未结束（{}），按 {} 处理：{}",
                    running, strategy.getValue(), job.getJobName());
            // SERIAL 的排队由执行池的队列天然承担：投进去就是排队。
            // 队列满时 submit 会返回 false，届时落 BLOCKED —— 即「有界排队，满了降级为丢弃」
            return strategy == SolvelaJobBlockStrategyEnum.DISCARD;
        }
        return false;
    }

    /**
     * 插 RUNNING 日志并投递。
     */
    private void dispatch(SolvelaJobEntity job, SolvelaJobHandlerMeta handler,
                          LocalDateTime triggerTime, LocalDateTime dbNow,
                          SolvelaJobTriggerSourceEnum source, int retrySeq,
                          String param, java.time.LocalDate bizDate,
                          Long retryOfLogId, String operator) {
        // ③ 集群级判据：阻塞则记录并终止。
        //    这条路径尚未插入自己的日志记录，所以没有要排除的 logId
        if (this.isBlocked(job, handler, dbNow, null)) {
            this.saveTerminalLog(job, triggerTime, dbNow, SolvelaJobExecuteStatusEnum.BLOCKED,
                    "上一次执行尚未结束，按阻塞策略丢弃本次");
            return;
        }

        SolvelaJobLogEntity logEntity = new SolvelaJobLogEntity();
        logEntity.setJobId(job.getJobId());
        logEntity.setJobName(job.getJobName());
        logEntity.setAppEnv(job.getAppEnv());
        logEntity.setTraceId(newTraceId());
        logEntity.setTriggerSource(source.getValue());
        logEntity.setTriggerTime(triggerTime);
        logEntity.setRetrySeq(retrySeq);
        logEntity.setBizDate(null != bizDate ? bizDate
                : triggerTime.toLocalDate().plusDays(handler.bizDateOffset()));
        logEntity.setParamSnapshot(param);
        logEntity.setStatus(SolvelaJobExecuteStatusEnum.RUNNING);
        logEntity.setExecuteStartTime(dbNow);
        logEntity.setExecuteTimeMillis(0L);
        logEntity.setScheduleDelayMs(this.calcDelayMillis(triggerTime, dbNow));
        logEntity.setRetryOfLogId(retryOfLogId);
        logEntity.setCreateName(null != operator ? operator : "system");
        this.fillNodeInfo(logEntity);

        try {
            jobRepository.saveLog(logEntity);
        } catch (DuplicateKeyException e) {
            // 🔴 这不是错误，是防重生效了：uk_job_trigger 告诉我们别的节点已经跑过这个触发点。
            //    抢占理论上保证单次，但网络分区、长 GC、时钟跳变下仍有窗口 ——
            //    把「不重复执行」从靠逻辑正确变成靠数据库约束，成本只是一个索引
            log.info("==== SolvelaJob ==== 该触发点已被其它节点执行，本次放弃：job={} trigger={}",
                    job.getJobName(), triggerTime);
            return;
        }

        if (!jobRunner.submit(job, logEntity, handler)) {
            SolvelaJobLogEntity update = new SolvelaJobLogEntity();
            update.setLogId(logEntity.getLogId());
            update.setStatus(SolvelaJobExecuteStatusEnum.BLOCKED);
            update.setExecuteEndTime(dbNow);
            update.setResultSummary("执行池已满，本次未投递");
            jobRepository.getJobLogDao().updateById(update);
        }
    }

    /**
     * 写一条「没真跑」的终态记录（MISFIRE / BLOCKED）。
     *
     * <p>不回写 {@code last_execute_time} —— 那一列的语义是「最后一次<b>执行</b>」，
     * 把「被丢弃」算进去会让运营误以为任务跑过了
     */
    private void saveTerminalLog(SolvelaJobEntity job, LocalDateTime triggerTime, LocalDateTime dbNow,
                                 SolvelaJobExecuteStatusEnum status, String summary) {
        SolvelaJobLogEntity logEntity = new SolvelaJobLogEntity();
        logEntity.setJobId(job.getJobId());
        logEntity.setJobName(job.getJobName());
        logEntity.setAppEnv(job.getAppEnv());
        logEntity.setTraceId(newTraceId());
        logEntity.setTriggerSource(SolvelaJobTriggerSourceEnum.SCHEDULE.getValue());
        logEntity.setTriggerTime(triggerTime);
        logEntity.setRetrySeq(0);
        logEntity.setParamSnapshot(job.getParam());
        logEntity.setStatus(status);
        logEntity.setExecuteStartTime(dbNow);
        logEntity.setExecuteEndTime(dbNow);
        logEntity.setExecuteTimeMillis(0L);
        logEntity.setScheduleDelayMs(this.calcDelayMillis(triggerTime, dbNow));
        logEntity.setResultSummary(summary);
        logEntity.setCreateName("system");
        this.fillNodeInfo(logEntity);
        try {
            jobRepository.saveLogOnly(logEntity);
        } catch (DuplicateKeyException e) {
            log.debug("==== SolvelaJob ==== 该触发点记录已存在，跳过：job={}", job.getJobName());
        }
    }

    private Long calcDelayMillis(LocalDateTime triggerTime, LocalDateTime actualStart) {
        if (null == triggerTime || null == actualStart) {
            return null;
        }
        return Duration.between(triggerTime, actualStart).toMillis();
    }

    private void fillNodeInfo(SolvelaJobLogEntity logEntity) {
        logEntity.setIp(nodeIp);
        logEntity.setProcessId(SolvelaJobUtil.getProcessId());
        logEntity.setProgramPath(SolvelaJobUtil.getProgramPath());
    }

    /**
     * 与 {@code LogTraceFilter} 同款：定时任务打的日志能和 web 请求走同一套检索
     */
    private static String newTraceId() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
    }

    @PreDestroy
    public void destroy() {
        // 🔴 停机第一件事是让扫描线程别再抢新任务，正在跑的由执行池按 shutdownAwaitSeconds 等待
        scanExecutor.shutdownNow();
        log.info("==== SolvelaJob ==== 扫描器已停止（不再抢占新任务）");
    }
}
