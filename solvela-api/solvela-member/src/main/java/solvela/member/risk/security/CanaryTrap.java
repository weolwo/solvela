package solvela.member.risk.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.module.config.ConfigKeyEnum;
import solvela.base.module.config.ConfigService;
import solvela.base.trace.Trace;
import solvela.member.risk.RiskContext;
import solvela.security.IpKeys;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import java.time.Duration;

/**
 * 有人用蜜罐账号登录了（{@code CANARY_ACCOUNT} 命中）：罚、记、告警（网关方案 §5.1、§5.2）。
 *
 * <ul>
 *   <li><b>罚</b>：IP 与设备各罚 {@code ban}（默认 24 小时），写的是网关安全闸读的那两个全局处罚键 ——
 *       下一个请求在网关第一道过滤器就结束，挡的是所有接口。IP 那一份<b>不豁免老设备</b>：没有真人会用蜜罐账号，
 *       「共用出口后面的真人」这个顾虑在这里不存在；</li>
 *   <li><b>记</b>：投一条事件（类型 2-蜜罐账号），落库任务把这个 IP 拉黑到处罚到期；</li>
 *   <li><b>告警</b>：ERROR —— 这条比别的都要紧，它说的是「我们的用户名单可能已经泄露」。</li>
 * </ul>
 *
 * <h3>🔴 不改变登录的回答</h3>
 * 这里只有副作用。登录照常往下走，在密码比对那一步失败（蜜罐的密码谁也不知道），回「账号或密码错误」——
 * 与真实账号输错密码逐字节相同、耗时相同。对方要到下一个请求才开始收到 429，分不出是哪一下暴露了。
 *
 * <p>任何异常都吞掉：罚不成只是少罚一次，绝不能让登录接口因此失败（那本身就是一个「这个号不一样」的信号）。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanaryTrap {

    private final StringRedisTemplate redis;

    private final ConfigService configService;

    private final SecurityIncidentQueue incidents;

    public void spring(RiskContext ctx) {
        try {
            long ban = GateSettings.parse(configService.getConfigValue(ConfigKeyEnum.GATEWAY_SECURITY_GATE)).banSeconds();
            long until = System.currentTimeMillis() / 1000 + ban;
            String ipKey = IpKeys.ip(ctx.getClientIp());
            if (ipKey != null) {
                redis.opsForValue().set(SecurityKeys.securityPenaltyIp(ipKey), String.valueOf(until), Duration.ofSeconds(ban));
            }
            if (ctx.getDeviceId() != null) {
                redis.opsForValue().set(SecurityKeys.securityPenaltyDevice(ctx.getDeviceId()), String.valueOf(until),
                        Duration.ofSeconds(ban));
            }
            SecurityIncidentEvent.Builder event = SecurityIncidentEvent
                    .builder(SecurityIncidentEvent.CANARY, "CANARY_ACCOUNT")
                    .penalty(null, until)
                    .who(ctx.getClientIp(), ctx.getDeviceId(), ctx.memberId())
                    .identityHash(ctx.getIdentityHashHex())
                    .request("POST", "/auth/login", null, Trace.id());
            if (ipKey != null) {
                event.action(SecurityIncidentEvent.ACTION_BLACK, until);
            }
            incidents.publish(event.build());
            log.error("【告警】【蜜罐账号】有人用蜜罐账号登录 —— 我们的用户名单可能已经泄露。IP 与设备已罚 {} 秒, 会员: {}, IP: {}, 设备: {}",
                    ban, ctx.memberId(), ctx.getClientIp(), ctx.getDeviceId());
        } catch (Exception e) {
            log.error("【告警】【蜜罐账号】有人用蜜罐账号登录，但处罚没写成, 会员: {}, IP: {}",
                    ctx.memberId(), ctx.getClientIp(), e);
        }
    }
}
