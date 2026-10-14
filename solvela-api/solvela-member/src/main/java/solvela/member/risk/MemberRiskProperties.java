package solvela.member.risk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录风控的引擎参数。<b>规则自己的阈值不在这里</b>，在 {@code t_member_risk_rule.params}（后台可改、不发版）。
 *
 * <pre>
 * solvela:
 *   member:
 *     risk:
 *       mode: enforce             # enforce | shadow
 *       pre-captcha-score: 30
 *       otp-score: 40
 *       strong-otp-score: 80
 *       cache-refresh: 30s
 * </pre>
 *
 * <h3>为什么分档线放配置、不放表</h3>
 * 它们是整套打分的标尺：改一次等于同时改了所有打分规则的效果。这种改动应该走发版评审，
 * 而不是在后台点一下（方案 §3.4）。
 *
 * @Date 2026-10-15
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.risk")
public class MemberRiskProperties {

    /**
     * 总开关。{@code shadow} 让<b>除全局滑块、黑名单、设备封禁以外</b>的规则全部只记录 ——
     * 上线后某条规则大面积误伤、又来不及定位是哪条时的保命开关。
     *
     * <p>刻意<b>没有 off</b>：旧的硬编码路径已经删掉，没有可以「退回」的地方；
     * 而「所有规则都不执行」等于把门全打开。
     */
    private Mode mode = Mode.ENFORCE;

    /** 前置得分达到它，结论至少是滑块 */
    private int preCaptchaScore = 30;

    /** 后置（前置分 + 后置分）达到它，结论至少是 OTP */
    private int otpScore = 40;

    /** 后置达到它，结论至少是换通道 OTP */
    private int strongOtpScore = 80;

    /** 规则表与名单的本地缓存多久重载一次。后台改完最多这么久生效 */
    private Duration cacheRefresh = Duration.ofSeconds(30);

    /**
     * 一次尝试里所有规则取特征的<b>总时间预算</b>。用完之后剩下的规则一律按未命中处理并记 error。
     *
     * <h3>🔴 为什么「慢」也要按「挂」处理</h3>
     * 只接异常是不够的：Redis 或画像查询变慢时不会抛，只会让每次登录都多等一会儿 ——
     * 而那正是最该快的接口。超了预算就不再取特征，宁可少判几条规则。
     *
     * <p>{@code alwaysEnforce} 的那几条（全局滑块、黑名单、设备封禁）<b>不受预算约束</b>：
     * 它们读的是本地缓存，本来就不花时间，而因为超预算放过一台被封的设备是不能接受的。
     */
    private Duration featureBudget = Duration.ofMillis(150);

    /** 单条规则执行超过它打一条 WARN：特征查询变慢的第一个信号 */
    private Duration slowRuleWarn = Duration.ofMillis(50);

    public enum Mode {
        ENFORCE,
        SHADOW,
    }

    public Duration cacheRefresh() {
        return cacheRefresh == null || cacheRefresh.isNegative() ? Duration.ofSeconds(30) : cacheRefresh;
    }

    public Duration featureBudget() {
        return featureBudget == null || featureBudget.isZero() || featureBudget.isNegative()
                ? Duration.ofMillis(150) : featureBudget;
    }
}
