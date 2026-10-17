package solvela.member.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.member.risk.feature.LoginCounters;

/**
 * 登录的 IP 维度闸门：失败次数、对不存在账号的尝试次数（单纯的请求总数 2026-10-17 移交网关限流）。
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
 * 不走 dry-run。代价是误伤共用出口的真人，所以阈值给得宽，见
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

    /**
     * 🔴 计数全部走它，本类不再自己拼 Redis 键。
     *
     * <p>每个计数器都有两侧：写的一侧在这里（失败、查无此人时 +1），读的一侧在风控规则里（超没超）。
     * 键名或窗口在两侧各写一遍，漂移的第一天不会有任何报错 —— 表现是「这条限流再也不触发」。
     * 所以键只在 {@link LoginCounters} 里出现一次。
     */
    private final LoginCounters counters;

    private final LoginIpGuardProperties properties;

    /**
     * 登录前的闸。放行返回 0，拦截返回还需等待的秒数（≥1）。
     *
     * <p>🔴 排在<b>查会员之前</b>：被限的 IP 不该还能拿登录接口去试探「这个号注册过没有」。
     * 失败与未知账号的计数<b>只读不加</b>（由 {@link #recordFailure} / {@link #recordUnknownAccount} 在真的发生时加）——
     * 在这里加的话，每一次正常登录都会被算成失败。
     */
    public long check(String ip) {
        if (!properties.isEnabled() || SolvelaStringUtil.isBlank(ip)) {
            return 0L;
        }
        if (counters.read(LoginCounters.Counter.IP_UNKNOWN, ip) > properties.getMaxUnknownAccounts()) {
            return hit(LoginCounters.Counter.IP_UNKNOWN, ip, "不存在账号尝试过多");
        }
        if (counters.read(LoginCounters.Counter.IP_FAIL, ip) > properties.getMaxFailures()) {
            return hit(LoginCounters.Counter.IP_FAIL, ip, "登录失败过多");
        }
        // 「单 IP 登录请求总数」2026-10-17 移交网关限流（LOGIN 场景的 IP 维度）：它只数次数、不看结果
        return 0L;
    }

    /** 记一次失败：密码错、验证码错、账号冻结 —— 能定位到「这次登录没成」的都算。 */
    public void recordFailure(String ip) {
        if (!properties.isEnabled() || SolvelaStringUtil.isBlank(ip)) {
            return;
        }
        counters.bump(LoginCounters.Counter.IP_FAIL, ip);
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
        long unknown = counters.bump(LoginCounters.Counter.IP_UNKNOWN, ip);
        counters.bump(LoginCounters.Counter.IP_FAIL, ip);
        // 这类尝试以前一行记录都没有（没有 member_id 写不了登录日志）。INFO 而不是 WARN：
        // 单次不说明问题，要看的是「同一个 IP 刷屏」，那时 check 会打 WARN
        log.info("【登录IP闸门】不存在的账号被尝试, ip: {}, 身份: {}, 本窗口第 {} 次", ip, maskedIdentity, unknown);
    }

    private long hit(LoginCounters.Counter counter, String ip, String rule) {
        long retryAfter = counters.retryAfterSeconds(counter, ip);
        log.warn("【登录IP闸门】拦截, ip: {}, 规则: {}, 还需等待 {} 秒", ip, rule, retryAfter);
        return retryAfter;
    }
}
