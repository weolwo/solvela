package solvela.admin.module.system.job.api;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.job.api.domain.SolvelaJobMsg;
import solvela.admin.module.system.job.config.SolvelaJobConfig;
import solvela.admin.module.system.job.core.SolvelaJobRunningRegistry;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

/**
 * 消息订阅端。
 *
 * <p>🔴 <b>抢占式调度之后，这个类几乎没活可干了 —— 这正是重构想要的结果。</b>
 *
 * <p>原实现里它承担着两件要命的事：在 Redisson 的<b>回调线程上同步执行整个任务</b>
 * （一个慢任务能占住回调线程池，波及全局的分布式锁与缓存），
 * 以及用一把和自动调度<b>互不排斥</b>的锁去防重（结果是同一任务可能并发跑两遍）。
 *
 * <p>现在这两件事都不存在了：
 * <ul>
 *   <li>手动触发由 {@code SolvelaJobService} 写一条 PENDING 日志，扫描线程自然会捞走执行 ——
 *       与定时调度走同一套抢占，天然互斥；</li>
 *   <li>配置变更由 {@code trigger_version} 天然感知，不需要通知。</li>
 * </ul>
 *
 * <p>所以 pub/sub <b>降级成了纯加速通道</b>：收到消息只是「提前唤醒一次扫描」，
 * 让生效从「最多 1 秒」变成「几乎立刻」。
 * <b>丢消息不再影响正确性</b> —— 这一点很关键，因为 pub/sub 本来就不保证送达，
 * 订阅端此刻掉线消息就永久丢了。把正确性建立在它上面是原设计最大的隐患。
 *
 * @author huke
 * @date 2024/6/22 20:31
 */
@Slf4j
public class SolvelaJobClientManager {

    /**
     * 🔴 topic 名带环境后缀。原来写死 {@code solvela-job-instance}：
     * dev 一旦连到与生产同源的 Redis，就会收到生产的触发消息 ——
     * 这种串台极难从现象倒推原因
     */
    private static final String TOPIC_PREFIX = "solvela-job-instance:";

    private final RTopic topic;

    private final SolvelaJobMsgListener jobMsgListener;

    private final SolvelaJobRunningRegistry runningRegistry;

    public SolvelaJobClientManager(SolvelaJobConfig jobConfig, RedissonClient redissonClient,
                                   SolvelaJobRunningRegistry runningRegistry) {
        this.runningRegistry = runningRegistry;
        this.topic = redissonClient.getTopic(TOPIC_PREFIX + jobConfig.getEnv());
        this.jobMsgListener = new SolvelaJobMsgListener();
        topic.addListener(SolvelaJobMsg.class, jobMsgListener);
        log.info("==== SolvelaJob ==== client-manager init, topic={}", TOPIC_PREFIX + jobConfig.getEnv());
    }

    /**
     * ⚠️ 这个方法跑在 Redisson 的回调线程上，<b>必须秒回</b>。
     *
     * <p>现在它真的只是记一行日志：真正的工作由扫描线程完成。
     * 扫描间隔本就是 1 秒，「提前唤醒」的收益有限，
     * 因此刻意<b>不</b>在这里去戳扫描线程 —— 那需要跨线程唤醒机制，
     * 为了省半秒引入一处并发复杂度不划算。
     */
    private class SolvelaJobMsgListener implements MessageListener<SolvelaJobMsg> {

        @Override
        public void onMessage(CharSequence channel, SolvelaJobMsg msg) {
            try {
                if (SolvelaJobMsg.MsgTypeEnum.TERMINATE_JOB == msg.getMsgType()) {
                    // 只有持有那个 Future 的节点会命中；其余节点查无此条，安静忽略。
                    // cancel 本身是非阻塞的，放在回调线程上没问题
                    boolean hit = runningRegistry.cancel(msg.getLogId());
                    if (hit) {
                        log.warn("==== SolvelaJob ==== 本节点已对 logId={} 发出中断信号（操作人 {}）",
                                msg.getLogId(), msg.getUpdateName());
                    }
                    return;
                }
                log.info("==== SolvelaJob ==== 收到任务变更通知（扫描线程将在下一轮生效）：{}", msg);
            } catch (Throwable t) {
                // 异常逃出去会污染 Redisson 的回调线程，且没有任何人会看到
                log.error("==== SolvelaJob ==== 消息处理异常：{}", msg, t);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        topic.removeListener(jobMsgListener);
    }
}
