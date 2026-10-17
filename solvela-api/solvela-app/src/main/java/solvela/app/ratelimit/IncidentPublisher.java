package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 把安全事件投进队列（{@link SecurityKeys#INCIDENT_QUEUE}），后台的落库任务写 {@code t_security_incident}、更新名单（网关方案 §6.4）。
 *
 * <h3>只在「状态变化」时投</h3>
 * 开始处罚、升档、被哪道保险压下了 —— 一次攻击几条事件。封禁期间被挡掉的每一个请求<b>不投</b>，
 * 只进 {@link BlockedCounter}：每挡一次写一条的话，一个每秒一万次的脚本就是每秒一万条记录。
 *
 * <h3>🔴 投不进去只打日志</h3>
 * 处罚已经写在 Redis 里、马上生效；事件只是留痕与名单。为了留痕让请求失败，是本末倒置。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
public class IncidentPublisher {

    private static final RedisScript<Long> PUSH = RedisScript.of(SecurityKeys.INCIDENT_PUSH_SCRIPT, Long.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final StringRedisTemplate redis;

    /** 生产固定是 {@link SecurityKeys#INCIDENT_QUEUE}；测试换一个，免得本机的落库任务把用例造的事件写进库 */
    private final String queueKey;

    @Autowired
    public IncidentPublisher(StringRedisTemplate redis) {
        this(redis, SecurityKeys.INCIDENT_QUEUE);
    }

    IncidentPublisher(StringRedisTemplate redis, String queueKey) {
        this.redis = redis;
        this.queueKey = queueKey;
    }

    public void publish(SecurityIncidentEvent event) {
        try {
            redis.execute(PUSH, List.of(queueKey), MAPPER.writeValueAsString(event),
                    String.valueOf(SecurityKeys.INCIDENT_QUEUE_MAX));
        } catch (Exception e) {
            log.error("【安全事件】投递失败，这一条只留在日志里, 类型: {}, 规则: {}, IP: {}",
                    event.incidentType(), event.ruleCode(), event.clientIp(), e);
        }
    }

    String queueKey() {
        return queueKey;
    }
}
