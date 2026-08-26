package solvela.admin.module.system.job.core;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.job.config.SolvelaJobConfig;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 快慢双车道执行池。
 *
 * <p>🔴 <b>物理隔离，不是逻辑分组。</b> 两个池、两套线程、两条队列。
 * 共用一个池时，两个跑 30 分钟的清洗任务能瞬间吃光线程，
 * 让每分钟一次的探活任务全部被丢弃 —— 而那种探活任务恰恰是
 * 「出事时唯一还能告诉你系统活着」的东西。
 *
 * <p>两条队列的容量刻意不对称：
 * <ul>
 *   <li>FAST 队列可以大 —— 短任务堆积很快消化；</li>
 *   <li>🔴 SLOW 队列近乎为 0 —— <b>长任务排队毫无意义</b>：排 30 分钟才开始跑，
 *       不如直接拒绝并告警，让「容量不够」这件事立刻可见。</li>
 * </ul>
 *
 * <p>{@link #hasCapacity} 是背压的判据。扫描线程<b>抢占前</b>先问一句，
 * 满了就跳过这条任务、不去抢占 —— 任务原封不动留在库里，
 * 下一轮任何一个有空位的节点自然会接走。这样「本该被别人执行的任务被就地误杀」不会发生。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SolvelaJobLaneExecutePool {

    private final Map<SolvelaJobLaneEnum, ThreadPoolTaskExecutor> poolMap = new EnumMap<>(SolvelaJobLaneEnum.class);

    /**
     * 只负责到点去 cancel，不跑业务，所以一根线程足够。
     *
     * <p>用「独立定时线程按点 cancel」而不是让提交方 {@code Future.get(timeout)} 干等：
     * 后者会把扫描线程变回阻塞的，等于白拆
     */
    private final ScheduledExecutorService timeoutScheduler;

    private final int shutdownAwaitSeconds;

    /**
     * 本节点在跑哪些执行记录 —— 支撑后台的「终止」操作。
     * 没有它，任务跑飞了只能重启服务，而重启会把其它正在跑的任务一起打断
     */
    private final SolvelaJobRunningRegistry runningRegistry;

    public SolvelaJobLaneExecutePool(SolvelaJobConfig config, SolvelaJobRunningRegistry runningRegistry) {
        this.shutdownAwaitSeconds = config.getShutdownAwaitSeconds();
        this.runningRegistry = runningRegistry;

        poolMap.put(SolvelaJobLaneEnum.FAST, buildPool("solvela-job-fast-",
                config.getFastCoreSize(), config.getFastMaxSize(), config.getFastQueueCapacity()));
        poolMap.put(SolvelaJobLaneEnum.SLOW, buildPool("solvela-job-slow-",
                config.getSlowCoreSize(), config.getSlowMaxSize(), config.getSlowQueueCapacity()));

        ThreadFactory timeoutFactory = Thread.ofPlatform().name("solvela-job-timeout-", 0).factory();
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(timeoutFactory);

        log.info("==== SolvelaJob ==== 双车道执行池就绪 FAST[{}/{}/{}] SLOW[{}/{}/{}] 停机等待={}s",
                config.getFastCoreSize(), config.getFastMaxSize(), config.getFastQueueCapacity(),
                config.getSlowCoreSize(), config.getSlowMaxSize(), config.getSlowQueueCapacity(),
                shutdownAwaitSeconds);
    }

    private ThreadPoolTaskExecutor buildPool(String namePrefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix(namePrefix);
        pool.setCorePoolSize(core);
        pool.setMaxPoolSize(max);
        pool.setQueueCapacity(queue);
        // 提交时把调用方 MDC 带过来，执行完原样还原（复用 logback 里已有的 %X{traceId}）
        pool.setTaskDecorator(new SolvelaJobMdcTaskDecorator());
        // 🔴 AbortPolicy 而不是 CallerRuns：CallerRuns 会让扫描线程亲自去跑那个任务，
        //    等于把「池满」变成「调度停摆」。Abort 抛出来，由调用方如实记录，池满才是可见的
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(shutdownAwaitSeconds);
        pool.initialize();
        return pool;
    }

    /**
     * 🔴 背压判据：该车道还吃得下吗。
     *
     * <p>扫描线程<b>抢占之前</b>调用。返回 false 时应当 {@code continue} 看下一条任务，
     * 而<b>不是</b> sleep 整轮 —— 第 1 条是慢任务且慢车道满了，
     * 但第 5 条是快任务而快车道空着，sleep 会把后者一起饿死。
     *
     * <p>判据用「活跃线程 + 队列积压 >= 最大线程 + 队列容量」而不是简单的水位百分比：
     * 我们要的不是「快满了」，而是「再投一个就会被拒」。
     */
    public boolean hasCapacity(SolvelaJobLaneEnum lane) {
        ThreadPoolTaskExecutor pool = poolMap.get(lane);
        if (null == pool) {
            return false;
        }
        ThreadPoolExecutor raw = pool.getThreadPoolExecutor();
        int inFlight = raw.getActiveCount() + raw.getQueue().size();
        int capacity = raw.getMaximumPoolSize() + raw.getQueue().remainingCapacity() + raw.getQueue().size();
        return inFlight < capacity;
    }

    /**
     * 投递并挂上超时中断。
     *
     * @param timeoutSeconds &lt;= 0 表示不限
     * @param body           入参是「是否因超时被中断」的标记：执行器据此把状态落成
     *                       TIMEOUT 而不是 FAIL —— 超时该调参数，失败才要查 bug
     * @return false 表示池满被拒。调用方必须如实记录，不能静默丢
     */
    public boolean submit(SolvelaJobLaneEnum lane, String jobName, Long logId,
                          int timeoutSeconds, Consumer<AtomicBoolean> body) {
        ThreadPoolTaskExecutor pool = poolMap.get(lane);
        if (null == pool) {
            log.error("==== SolvelaJob ==== 未知车道 {}，任务未投递：{}", lane, jobName);
            return false;
        }
        AtomicBoolean timedOut = new AtomicBoolean(false);
        Future<?> future;
        try {
            future = pool.submit(() -> {
                try {
                    body.accept(timedOut);
                } finally {
                    runningRegistry.unregister(logId);
                }
            });
            runningRegistry.register(logId, future);
            // 🔴 补一次判断，闭合竞态：任务可能在 register 之前就跑完了，
            //    那时 finally 里的 unregister 先执行，register 再把它塞回去 —— 登记表就泄漏了
            if (future.isDone()) {
                runningRegistry.unregister(logId);
            }
        } catch (TaskRejectedException e) {
            // ⚠️ 判水位与真正 submit 之间存在竞态：别的任务可能刚好把池占满。
            //    概率低且此处可见，接受 —— 但绝不能让它变成未捕获异常
            log.error("==== SolvelaJob ==== {} 车道已满，任务被拒：{}", lane.getValue(), jobName);
            return false;
        }
        if (timeoutSeconds > 0) {
            // 任务提前结束时这个哨兵不会被主动取消，它到点空跑一次即退出。
            // 用一个队列条目换掉「提交方阻塞等待」，这笔交易划算
            timeoutScheduler.schedule(() -> {
                if (!future.isDone()) {
                    timedOut.set(true);
                    future.cancel(true);
                    log.error("==== SolvelaJob ==== 任务执行超时({}s)，已发出中断：{}", timeoutSeconds, jobName);
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
        }
        return true;
    }

    @PreDestroy
    public void destroy() {
        log.info("==== SolvelaJob ==== 执行池开始停机，最长等待 {}s", shutdownAwaitSeconds);
        timeoutScheduler.shutdownNow();
        poolMap.values().forEach(ThreadPoolTaskExecutor::shutdown);
        log.info("==== SolvelaJob ==== 执行池已停机");
    }
}
