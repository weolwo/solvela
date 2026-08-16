package sa.base.module.support.job.api;

import lombok.extern.slf4j.Slf4j;
import sa.base.common.util.SmartRandomUtil;
import sa.base.module.support.job.api.domain.SmartJobMsg;
import sa.base.module.support.job.config.SmartJobConfig;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

/**
 * 只负责「往执行端发消息」的一半。
 *
 * <p>🔴 <b>从 {@link SmartJobClientManager} 里拆出来，是为了让 ADMIN 节点能用。</b>
 * 原实现把「发消息」和「收消息并执行」焊在同一个类里，而那个类依赖执行侧的组件 ——
 * 于是不执行任务的节点连「通知别人去执行」都做不到，
 * 后台的「立即执行」「保存后生效」全都失灵。这正是独立部署被卡住的地方之一。
 *
 * <p>拆开之后：ADMIN 只装本类（发），WORKER 装 {@link SmartJobClientManager}（收 + 执行），
 * ALL 两个都装。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SmartJobMsgPublisher {

    private static final String TOPIC_PREFIX = "smart-job-instance:";

    private final RTopic topic;

    public SmartJobMsgPublisher(SmartJobConfig jobConfig, RedissonClient redissonClient) {
        this.topic = redissonClient.getTopic(TOPIC_PREFIX + jobConfig.getEnv());
        log.info("==== SmartJob ==== msg-publisher init, topic={}", TOPIC_PREFIX + jobConfig.getEnv());
    }

    /**
     * 发布消息给所有执行端。
     *
     * <p>⚠️ pub/sub <b>不保证送达</b>：订阅端此刻掉线，这条消息就永久丢了。
     * 本档它仍是「配置变更立即生效」的唯一通道，兜底靠 launcher 的定时轮询
     * （{@code db-refresh-interval}，默认 60 秒）。
     * 第二档换成数据库抢占式调度后，正确性由 {@code trigger_version} 保证，
     * pub/sub 会退化成纯加速通道 —— 那时丢消息最多晚一秒生效，不再影响正确性。
     */
    public void publishToClient(SmartJobMsg msgDTO) {
        msgDTO.setMsgId(SmartRandomUtil.simpleUuid());
        topic.publish(msgDTO);
    }
}
