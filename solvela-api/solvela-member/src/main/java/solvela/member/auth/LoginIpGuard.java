package solvela.member.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;

/**
 * 登录的 IP 维度闸门：总次数、失败次数、对不存在账号的尝试次数。
 *
 * <h3>为什么设备与账号两个维度还不够</h3>
 * 一个撞库脚本<b>不带设备令牌</b>（设备闸门对「没有设备」一律放行）、<b>每个账号只试一次</b>
 * （账号锁要同一个号错 5 次才触发）—— 两道闸都数不到它。它唯一藏不住的是「从哪来」。
 *
 * <h3>「不存在的账号」要单独数</h3>
 * 查不到人时没有 member_id，登录日志一行都写不了 —— 这类尝试以前完全不可见。
 * 而它恰恰是最强的信号：真人偶尔记错注册邮箱，一个 IP 一小时试十个不存在的账号只可能是脚本。
 *
 * <h3>上线即拦截</h3>
 * 不走 dry-run（与 {@code DeviceGuard} 不同）。代价是误伤共用出口的真人，所以阈值给得宽，见
 * {@link LoginIpGuardProperties}。计数全在 Redis，窗口自然过期。
 *
 * <p>IP 为空时一律放行：只可能是调用方没传（内部工具、测试），不该因此把人挡在外面。
 *
 * @Date 2026-09-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginIpGuard {

    private static final String KEY_ATTEMPT = "login:ip:attempt:";

    private static final String KEY_FAIL = "login:ip:fail:";

    private static final String KEY_UNKNOWN = "login:ip:unknown:";

    private final RedisService redisService;

    private final LoginIpGuardProperties properties;

    /**
     * 登录前的闸。放行返回 0，拦截返回还需等待的秒数（≥1）。
     *
     * <p>🔴 排在<b>查会员之前</b>：被限的 IP 不该还能拿登录接口去试探「这个号注册过没有」。
     * 失败与未知账号的计数<b>只读不加</b>（由 {@link #recordFailure} / {@link #recordUnknownAccount} 在真的发生时加），
     * 总次数在这里加 —— 在这里加失败计数会把每一次正常登录也算成失败。
     */
    public long check(String ip) {
        if (!properties.isEnabled() || SolvelaStringUtil.isBlank(ip)) {
            return 0L;
        }
        if (read(KEY_UNKNOWN, ip) > properties.getMaxUnknownAccounts()) {
            return hit(KEY_UNKNOWN, ip, "不存在账号尝试过多");
        }
        if (read(KEY_FAIL, ip) > properties.getMaxFailures()) {
            return hit(KEY_FAIL, ip, "登录失败过多");
        }
        long attempts = redisService.increment(key(KEY_ATTEMPT, ip), properties.window().toSeconds());
        if (attempts > properties.getMaxAttempts()) {
            return hit(KEY_ATTEMPT, ip, "登录请求过多");
        }
        return 0L;
    }

    /** 记一次失败：密码错、验证码错、账号冻结 —— 能定位到「这次登录没成」的都算。 */
    public void recordFailure(String ip) {
        if (!properties.isEnabled() || SolvelaStringUtil.isBlank(ip)) {
            return;
        }
        redisService.increment(key(KEY_FAIL, ip), properties.window().toSeconds());
    }

    /**
     * 记一次对<b>不存在账号</b>的尝试。同时算一次失败。
     *
     * @param maskedIdentity 打过码的身份（邮箱 / 手机号），只进日志
     */
    public void recordUnknownAccount(String ip, String maskedIdentity) {
        if (!properties.isEnabled() || SolvelaStringUtil.isBlank(ip)) {
            return;
        }
        long unknown = redisService.increment(key(KEY_UNKNOWN, ip), properties.window().toSeconds());
        redisService.increment(key(KEY_FAIL, ip), properties.window().toSeconds());
        // 这类尝试以前一行记录都没有（没有 member_id 写不了登录日志）。INFO 而不是 WARN：
        // 单次不说明问题，要看的是「同一个 IP 刷屏」，那时 check 会打 WARN
        log.info("【登录IP闸门】不存在的账号被尝试, ip: {}, 身份: {}, 本窗口第 {} 次", ip, maskedIdentity, unknown);
    }

    private long hit(String prefix, String ip, String rule) {
        long retryAfter = Math.max(1L, redisService.getExpire(key(prefix, ip)));
        log.warn("【登录IP闸门】拦截, ip: {}, 规则: {}, 还需等待 {} 秒", ip, rule, retryAfter);
        return retryAfter;
    }

    private long read(String prefix, String ip) {
        String raw = redisService.get(key(prefix, ip));
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            // 只可能是有人手改了 Redis。当成 0：一个读不出来的计数器不该让整个网段登不进来
            return 0L;
        }
    }

    private String key(String prefix, String ip) {
        return redisService.generateRedisKey(prefix, ip);
    }
}
