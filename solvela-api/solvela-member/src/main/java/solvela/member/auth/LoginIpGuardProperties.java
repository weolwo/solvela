package solvela.member.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录的 IP 维度闸门参数。
 *
 * <pre>
 * solvela:
 *   member:
 *     login:
 *       ip-guard:
 *         enabled: true
 *         window: 1h
 *         max-attempts: 60
 *         max-failures: 20
 *         max-unknown-accounts: 10
 * </pre>
 *
 * <h3>🔴 阈值刻意给得宽</h3>
 * IP 维度最容易误伤：公司出口、校园网、运营商大 NAT 背后是成百上千个真人。
 * 60 次 / 小时对一个真人几乎不可能撞到，对一个撞库脚本几分钟就撞到。
 * 上线即拦截（不走 dry-run），所以宁可宽一点 —— 真被误伤时调这里即可，不用发版。
 *
 * @Date 2026-09-27
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.login.ip-guard")
public class LoginIpGuardProperties {

    /** 总开关。关掉等于只剩设备与账号两个维度 —— 仅用于误伤排障时的临时回退 */
    private boolean enabled = true;

    /** 计数窗口 */
    private Duration window = Duration.ofHours(1);

    /** 一个 IP 一个窗口内的登录请求总数（含成功） */
    private int maxAttempts = 60;

    /** 一个 IP 一个窗口内的登录失败次数（密码错、账号不存在、验证码错） */
    private int maxFailures = 20;

    /**
     * 一个 IP 一个窗口内，对<b>不存在的账号</b>的尝试次数。
     *
     * <p>四个计数里信号最强的一个：真人偶尔记错自己用哪个邮箱注册，一小时内试十个不存在的账号
     * 只可能是在批量撞库或枚举。它也补上了一个盲区 —— 查不到人时没有 member_id，
     * 登录日志一行都写不了，这类尝试以前完全看不见。
     */
    private int maxUnknownAccounts = 10;

    public Duration window() {
        return window == null || window.isZero() || window.isNegative() ? Duration.ofHours(1) : window;
    }
}
