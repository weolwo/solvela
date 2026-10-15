package solvela.member.risk.feature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import solvela.base.module.redis.RedisService;
import solvela.member.risk.MemberRiskProperties;
import solvela.member.risk.dao.MemberActivityDao;

import java.time.Duration;

/**
 * 刷新「这个会员最近还在用」。写的是 {@code t_member.last_active_time}。
 *
 * <h3>🔴 为什么不能用「最后一次登录」代替</h3>
 * 会话令牌 30 天有效而且<b>不续期</b>。一个每天都在用的人，每 30 天才重新登录一次 ——
 * 按登录时间算的话，<b>每个活跃用户每个月都会被判一次「沉睡」</b>，于是人人都要多验一道码。
 * 这是登录风控里「沉睡」那一维唯一能用的数据来源。
 *
 * <h3>节流：一小时最多写一次库</h3>
 * 做法与 {@code DeviceDispositionService.touch} 完全一致（{@code SET NX} 抢到才写）。
 * {@code t_member} 是热读表（每次鉴权都读），不能挂一个每请求都写的字段 ——
 * 那张表的 DDL 注释里专门写过这件事，{@code last_login_time} 就是因此被移出去的。
 *
 * <p>Redis 异常时<b>不写</b>（{@code setIfAbsent} 返回 null 也走这条）：
 * 宁可少一次刷新，也不要在 Redis 挂掉时把这张表打满。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberActivityService {

    private static final String KEY_ACTIVE = "mbr:active:";

    private final MemberActivityDao activityDao;

    private final RedisService redisService;

    private final StringRedisTemplate redis;

    private final MemberRiskProperties properties;

    /** 有业务请求就调一次。任何异常都只记日志 —— 一条纯粹的记录不该让用户的请求失败 */
    public void touch(Long memberId) {
        if (memberId == null) {
            return;
        }
        try {
            Duration throttle = properties.activeThrottle();
            Boolean won = redis.opsForValue()
                    .setIfAbsent(redisService.generateRedisKey(KEY_ACTIVE, String.valueOf(memberId)), "1", throttle);
            if (!Boolean.TRUE.equals(won)) {
                return;
            }
            activityDao.touchActive(memberId);
        } catch (Exception e) {
            log.warn("【会员活跃】刷新失败，已忽略, memberId: {}", memberId, e);
        }
    }
}
