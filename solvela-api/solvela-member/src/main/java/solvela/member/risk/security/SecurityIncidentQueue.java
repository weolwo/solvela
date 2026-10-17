package solvela.member.risk.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import solvela.base.json.JsonUtils;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import java.util.List;

/**
 * 会员域往安全事件队列（{@link SecurityKeys#INCIDENT_QUEUE}）投事件 —— 今天只有蜜罐账号（{@link CanaryTrap}）。
 * 与网关的 {@code IncidentPublisher} 用同一段脚本（{@link SecurityKeys#INCIDENT_PUSH_SCRIPT}）、同一个格式。
 *
 * <p>🔴 投不进去只打日志：处罚已经写在 Redis 里，事件只是留痕与名单。为了留痕让登录接口失败，是本末倒置。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
public class SecurityIncidentQueue {

    private static final RedisScript<Long> PUSH = RedisScript.of(SecurityKeys.INCIDENT_PUSH_SCRIPT, Long.class);

    private final StringRedisTemplate redis;

    private final String queueKey;

    @Autowired
    public SecurityIncidentQueue(StringRedisTemplate redis) {
        this(redis, SecurityKeys.INCIDENT_QUEUE);
    }

    /** 测试换一个队列：本机的落库任务不会把用例造的事件写进库 */
    public SecurityIncidentQueue(StringRedisTemplate redis, String queueKey) {
        this.redis = redis;
        this.queueKey = queueKey;
    }

    public void publish(SecurityIncidentEvent event) {
        try {
            redis.execute(PUSH, List.of(queueKey), JsonUtils.toJson(event), String.valueOf(SecurityKeys.INCIDENT_QUEUE_MAX));
        } catch (Exception e) {
            log.error("【安全事件】投递失败，这一条只留在日志里, 类型: {}, 规则: {}, IP: {}",
                    event.incidentType(), event.ruleCode(), event.clientIp(), e);
        }
    }
}
