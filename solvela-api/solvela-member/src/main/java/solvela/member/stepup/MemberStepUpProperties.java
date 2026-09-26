package solvela.member.stepup;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 二次验证（设备信任）参数。
 *
 * <pre>
 * solvela:
 *   member:
 *     step-up:
 *       trust-after: 7d
 *       verified-ttl: 180d
 * </pre>
 *
 * <p>「哪些接口要验、拦不拦」不在这里 —— 那是网关的事（{@code solvela.app.step-up.mode}
 * 与接口上的 {@code @StepUpRequired}）。这里只定义<b>什么叫一台受信任的设备</b>，
 * 那是会员域的规则。
 *
 * @Date 2026-09-26
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.step-up")
public class MemberStepUpProperties {

    /**
     * 存量设备的信任门槛：这台设备上该会员的成功登录早于这么久，就算受信任。
     *
     * <p>这是一个取舍，不是一个安全边界：盗号者只要在受害者不察觉的情况下
     * 潜伏这么久，也能拿到信任。调短对用户更友好，攻击者的等待成本也随之降低。
     */
    private Duration trustAfter = Duration.ofDays(7);

    /**
     * 通过二次验证之后，这台设备被信任多久。
     *
     * <p>不给永久：一台很久没用的设备（旧手机转手、网吧电脑）不该一直保留信任。
     * 每次再验证通过都会续期。
     */
    private Duration verifiedTtl = Duration.ofDays(180);

    public Duration trustAfter() {
        return trustAfter == null || trustAfter.isNegative() ? Duration.ofDays(7) : trustAfter;
    }

    public Duration verifiedTtl() {
        return verifiedTtl == null || verifiedTtl.isZero() || verifiedTtl.isNegative()
                ? Duration.ofDays(180)
                : verifiedTtl;
    }
}
