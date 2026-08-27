package solvela.admin.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import solvela.admin.module.system.operatelog.core.OperateLogAspect;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 守住操作日志线程池的队列是<b>有界</b>的。
 *
 * <p>{@code ThreadPoolTaskExecutor} 的队列是在 {@code initialize()} 里按当时的
 * {@code queueCapacity} 建出来的，之后再调 {@code setQueueCapacity} 只改字段、不换队列。
 * 而 {@code setCorePoolSize} / {@code setMaxPoolSize} / {@code setRejectedExecutionHandler}
 * 都会转发给已经建好的池 —— 所以一旦把 {@code initialize()} 写在 setter 前面，
 * <b>只有队列这一项静默失效</b>，其余全部生效，看代码根本发现不了。
 *
 * <p>失效的后果不是"队列大了点"：无界队列<b>永远不会拒绝任务</b>，
 * 配在下面的 {@code CallerRunsPolicy} 等于没配。操作日志本来是靠拒绝策略回压到调用线程的，
 * 现在改成在堆里一路堆积 —— 平时看不出来，某次批量操作或下游变慢时直接 OOM。
 *
 * <p>这个测试断言的是最终状态，不是写法，所以不管将来怎么重构都还成立。
 */
@SpringBootTest
class OperateLogExecutorTest {

    @Autowired(required = false)
    private OperateLogAspect aspect;

    @Test
    @DisplayName("操作日志线程池的队列必须有界，否则拒绝策略形同虚设")
    void 队列有界() {
        assertNotNull(aspect, "OperateLogAspect 不在容器里了？那本测试要跟着调整");

        ThreadPoolTaskExecutor executor =
                (ThreadPoolTaskExecutor) ReflectionTestUtils.getField(aspect, "taskExecutor");
        assertNotNull(executor, "切面还没初始化线程池");

        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        int remaining = pool.getQueue().remainingCapacity();

        assertNotEquals(Integer.MAX_VALUE, remaining,
                "队列是无界的 —— 多半是 initialize() 又被挪到了 setQueueCapacity 前面。"
                        + " 无界队列不会触发 CallerRunsPolicy，日志积压时没有任何背压。");
        assertEquals(1000, remaining, "队列容量与 initThread() 里配置的 1000 不一致");
    }
}
