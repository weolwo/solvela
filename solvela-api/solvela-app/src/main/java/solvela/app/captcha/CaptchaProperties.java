package solvela.app.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 滑块验证码。
 *
 * <pre>
 * solvela:
 *   app:
 *     captcha:
 *       send-code: true         # 发邮箱 / 短信验证码之前
 *       password-login: true    # 密码登录之前
 *       tolerance: 5
 *       max-create-per-ip-per-minute: 20
 * </pre>
 *
 * <h3>拦在哪</h3>
 * <ul>
 *   <li><b>发码</b>：邮件和短信都是真金白银、也是轰炸的原料，而发码接口天生匿名；</li>
 *   <li><b>密码登录</b>：撞库的主战场。邮箱验证码登录不拦 —— 它的码在发码那一步已经过过一次滑块。</li>
 * </ul>
 * 每个场景单独一个开关：误伤时只关那一处，不用整体下线。
 *
 * @param sendCode                  发码前是否要求滑块
 * @param passwordLogin             密码登录前是否要求滑块
 * @param tolerance                 拖到的位置与缺口的允许误差（图像像素）
 * @param challengeTtl              一张图的有效期
 * @param passTtl                   通过之后那张通行票的有效期
 * @param maxCreatePerIpPerMinute   同一 IP 每分钟最多出几张图 —— 出图要吃 CPU，不限就是一个免费的压测口
 *
 * @Date 2026-09-27
 */
@ConfigurationProperties(prefix = "solvela.app.captcha")
public record CaptchaProperties(Boolean sendCode, Boolean passwordLogin, Integer tolerance,
                                Duration challengeTtl, Duration passTtl, Integer maxCreatePerIpPerMinute) {

    public CaptchaProperties {
        sendCode = sendCode == null || sendCode;
        passwordLogin = passwordLogin == null || passwordLogin;
        tolerance = tolerance == null || tolerance < 1 ? 5 : tolerance;
        challengeTtl = challengeTtl == null ? Duration.ofMinutes(2) : challengeTtl;
        passTtl = passTtl == null ? Duration.ofMinutes(2) : passTtl;
        maxCreatePerIpPerMinute = maxCreatePerIpPerMinute == null || maxCreatePerIpPerMinute < 1
                ? 20 : maxCreatePerIpPerMinute;
    }
}
