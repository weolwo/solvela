package solvela.member.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 验证码邮件<b>发送</b>专用线程池。只承载「把信真的交给 SMTP」这一步。
 *
 * <h3>为什么发信必须离开请求线程</h3>
 * 同步发一封信要 ~3.4s（SMTP 到 Gmail 的握手 + 投递）。而网关调本服务的
 * {@code MemberAuthApi} 读超时是 1s —— 于是 2026-09-12 上线当天：信真发出去了，
 * 网关却因 1s 超时给用户报了 500。用户收到了码，页面却说「服务开小差」，
 * 然后重试，触发重复发信 + 撞限频。
 *
 * <p>决定响应的是<b>快的那几步</b>（格式校验、限频、场景是否该寄），它们全在
 * Redis / 内存里，毫秒级；真正慢的 SMTP 投递本就不该让调用方等。
 * 把它挪到这里之后，那个 1s 超时反而是<b>对的</b>：内部调用确实该在 1s 内返回。
 *
 * <h3>🔴 为什么不复用 {@code solvela-async-executor}</h3>
 * 与 {@code TaskEventExecutorConfig} 同样的两条理由，这个项目已经踩过：
 * <ul>
 *   <li><b>那个池队列无界</b>（{@code AsyncConfig} 没设 queueCapacity，默认
 *       {@code Integer.MAX_VALUE}）—— 慢 SMTP 下积压不降级，直接堆到 OOM；</li>
 *   <li><b>那个池正被派奖链路占用</b>（{@code GlobalEventDispatcher} 发资产，
 *       与本服务同在 app-biz 进程）。发信是 I/O 密集又偏慢的活，挤进去会让
 *       「中奖了积分半天不到账」，而两条链路看着毫不相关，根因极难联想。</li>
 * </ul>
 *
 * <h3>拒绝策略 AbortPolicy —— 队满时同步兜底</h3>
 * submit 是在请求线程上同步发生的，所以队满被拒会<b>当场</b>抛
 * {@code RejectedExecutionException}。{@code MemberEmailCodeService} 捕获它、
 * 把刚存的码删掉（否则用户收不到信还被冷却挡住）、返回发送失败让用户重试。
 * 也就是说「排不下」这种情况仍然是同步、可感知的，不会静默丢。
 */
@Slf4j
@Configuration
public class EmailSendExecutorConfig {

    public static final String EMAIL_SEND_EXECUTOR = "email-send-executor";

    /**
     * 队列容量。验证码发送在上游已被「每邮箱每天 / 每 IP 每天」限频，
     * 正常不会堆积；这个容量是给「SMTP 短时变慢」留的缓冲，满了就靠拒绝策略兜底。
     */
    private static final int QUEUE_CAPACITY = 500;

    private static final int KEEP_ALIVE_SECONDS = 60;

    @Bean(name = EMAIL_SEND_EXECUTOR)
    public AsyncTaskExecutor emailSendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // SMTP 是 I/O 等待，不吃 CPU，但并发连接数厂商有限制（Gmail 尤其严），
        // 所以线程数给小、固定，不随 CPU 核数放大。
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(EMAIL_SEND_EXECUTOR + "-");
        // 显式写出来：默认也是 Abort，但这个取舍要在代码里看得见 ——
        // 改成 CallerRuns 会让发信重新阻塞请求线程，正是本类要避免的事。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 关停等一会儿把在途的信发完：验证码就几秒，等它发完比丢掉体验好。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();

        log.info("[验证码邮件线程池] 已初始化: core=2, max=4, queue={}, 拒绝策略=AbortPolicy",
                QUEUE_CAPACITY);
        return executor;
    }
}
