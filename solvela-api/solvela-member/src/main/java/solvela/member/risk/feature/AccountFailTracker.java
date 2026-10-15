package solvela.member.risk.feature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * 一个账号最近的登录失败<b>来自哪些 IP</b>。
 *
 * <h3>🔴 为什么不能从登录日志数失败</h3>
 * {@code t_member_login_log} 里的 {@code LOGIN_FAIL} <b>不全是失败</b>：
 * 签二次验证凭票写的是 LOGIN_FAIL，被冷却挡下写的也是 LOGIN_FAIL。
 * 按 status 去数的话，一个在观察档设备上<b>正常登录</b>的人每次都会给自己记一次「失败」——
 * 于是他自己把自己推进「账号被围攻」那条规则里。所以单独记一份。
 *
 * <h3>它补的是冷却的盲区</h3>
 * 密码冷却数的是<b>次数</b>：攻击者从五个 IP 各试两次，每个桶都没到阈值。
 * 而「同一个号、一小时内、来自好几个不同 IP 的失败」只可能是有人在围攻这个账号。
 *
 * <h3>结构：有序集合，成员带一次性后缀</h3>
 * 成员直接用 IP 的话，同一个 IP 失败十次只会留下一条 —— 次数就数不出来了。
 * 所以成员是 {@code IP#序号}，按时间打分：区间查一次就同时得到「几次」和「几个不同 IP」。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountFailTracker {

    private static final String KEY = "mbr:risk:fail:";

    private static final String SEP = "#";

    /** 一个账号最多留这么多条。被围攻时记录会很快堆起来，而判断只要「够不够阈值」 */
    private static final int MAX_ENTRIES = 50;

    private final RedisService redisService;

    private final StringRedisTemplate redis;

    /** 近 window 内的失败次数与不同来源数 */
    public record Burst(int failures, int distinctIps) {

        public static final Burst NONE = new Burst(0, 0);
    }

    /** 记一次失败。任何异常都只记日志 —— 它在登录的失败路径上，不该让响应变成 500 */
    public void record(Long memberId, String clientIp, Duration window) {
        if (memberId == null || SolvelaStringUtil.isBlank(clientIp)) {
            return;
        }
        try {
            String key = key(memberId);
            long now = System.currentTimeMillis();
            redis.opsForZSet().add(key, clientIp + SEP + now, now);
            // 顺手把窗口外的清掉：不清的话这个集合会一直长，而过期成员对判断毫无用处
            redis.opsForZSet().removeRangeByScore(key, 0, now - window.toMillis());
            // 只留最近的那几条（0 是最旧的一端）
            redis.opsForZSet().removeRange(key, 0, -(MAX_ENTRIES + 1L));
            redis.expire(key, window);
        } catch (RuntimeException e) {
            log.warn("【账号失败来源】记录失败，已忽略, memberId: {}", memberId, e);
        }
    }

    /** 查近 window 内的失败情况 */
    public Burst burst(Long memberId, Duration window) {
        if (memberId == null) {
            return Burst.NONE;
        }
        long now = System.currentTimeMillis();
        Set<String> entries = redis.opsForZSet()
                .rangeByScore(key(memberId), now - window.toMillis(), now);
        if (entries == null || entries.isEmpty()) {
            return Burst.NONE;
        }
        Set<String> ips = new HashSet<>();
        for (String entry : entries) {
            int at = entry.lastIndexOf(SEP);
            ips.add(at < 0 ? entry : entry.substring(0, at));
        }
        return new Burst(entries.size(), ips.size());
    }

    public void clear(Long memberId) {
        if (memberId != null) {
            redisService.delete(key(memberId));
        }
    }

    private String key(Long memberId) {
        return redisService.generateRedisKey(KEY, String.valueOf(memberId));
    }
}
