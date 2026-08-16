package sa.task.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 任务事件专用线程池（方案 §4.7，P0.5 异步隔离）。
 *
 * <p>🔴 <b>为什么不复用 {@code AsyncConfig} 里那个 {@code smart-async-executor}</b>，两条理由：
 *
 * <p><b>① 那个池的队列是无界的。</b>
 * {@code AsyncConfig} 只设了 corePoolSize / maxPoolSize，<b>没有调用 setQueueCapacity</b>，
 * 而 {@code ThreadPoolTaskExecutor} 的默认队列容量是 {@code Integer.MAX_VALUE}。
 * 后果不是「降级」而是「OOM」：拒绝策略永远不会触发，积压全部堆在堆内存里；
 * 顺带 maxPoolSize 也永远不会生效（队列不满就不扩容，是 ThreadPoolExecutor 的固有行为）。
 *
 * <p><b>② 那个池正被派奖链路占用。</b>
 * {@code GlobalEventDispatcher} 用的就是它，承载的是抽奖/彩票的<b>资产派发</b>
 * （已压测验证、直接关系到用户能不能拿到钱）。任务事件若挤进同一个队列，
 * 一次事件洪峰会让派奖任务排在后面 —— 表现是「抽奖中奖了但积分半天不到账」，
 * 而两条链路看起来毫无关系，根因极难联想。
 * 派奖是资产链路、任务事件是进度链路，<b>重要性不对等，不该共享隔板</b>（舱壁隔离）。
 *
 * <p><b>拒绝策略选 AbortPolicy 是一个明确的取舍</b>：
 * {@code CallerRunsPolicy} 不丢事件，但会<b>反向阻塞上游</b> —— 恰好破坏了异步隔离的初衷，
 * 而这个模块存在的前提就是「任务系统的抖动绝不能拖死主交易链路」。
 * 选 Abort 意味着队列打满时会丢事件，因此<b>必须</b>配套：
 * 被拒事件落 {@code t_task_record_flow} 的丢弃流水 + 接口如实返回失败让上游可重试
 * （见 {@code TaskEventService.report}）。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Slf4j
@Configuration
public class TaskEventExecutorConfig {

    public static final String TASK_EVENT_EXECUTOR = "task-event-executor";

    /**
     * 队列容量。有界是重点，具体数值不是 ——
     * 它只决定「洪峰能缓冲多久」，缓冲不下时靠拒绝策略保护上游，而不是靠把队列开大。
     */
    private static final int QUEUE_CAPACITY = 2000;

    /**
     * 空闲线程存活时间：任务事件是突发型负载，峰谷差大，让扩出来的线程能回收
     */
    private static final int KEEP_ALIVE_SECONDS = 60;

    @Bean(name = TASK_EVENT_EXECUTOR)
    public AsyncTaskExecutor taskEventExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(2, processors / 2));
        executor.setMaxPoolSize(Math.max(4, processors));
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(TASK_EVENT_EXECUTOR + "-");
        // 显式声明拒绝策略：默认也是 AbortPolicy，但这里的取舍必须在代码里看得见，
        // 否则将来有人把它改成 CallerRuns 时不会意识到自己关掉了异步隔离
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 关停时不等待：任务进度可以靠事件重投补，卡住优雅停机反而影响发布
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();

        log.info("[任务事件线程池] 已初始化: core={}, max={}, queue={}, 拒绝策略=AbortPolicy",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), QUEUE_CAPACITY);
        return executor;
    }
}
