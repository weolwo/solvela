package solvela.member.device;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 设备<b>使用</b>侧的限流参数。签发侧那一道在 {@link DeviceProperties}。
 *
 * <pre>
 * solvela:
 *   member:
 *     device:
 *       guard:
 *         dry-run: true          # 只计数、只打日志，不拦
 *         max-login-per-day: 30
 *         max-fail-per-hour: 10
 *         max-members-per-day: 3
 *         max-register-per-day: 2
 * </pre>
 *
 * <h3>🔴 dry-run 默认开着，而且应当开一阵子</h3>
 * 下面这四个阈值全是<b>拍出来的</b> —— 没有任何真实分布支撑。直接开拦截，
 * 最可能的结果不是拦住刷子，是拦住一整栋写字楼里共用出口的正常用户，
 * 而<b>他们不会来报障，只会不再打开</b>。
 *
 * <p>所以先跑 dry-run：命中什么都照常放行，只往日志里记一条「本来会被拦」。
 * 拿真实流量把这四个数校准完再关掉它。
 *
 * <p>⚠️ 关掉 dry-run 之前还差一样东西：<b>验证码</b>。方案里这几条的处置是
 * 「降级为需要验证码」，而验证码还没接 —— 现在关掉 dry-run 就只能硬拒绝，
 * 那是误伤代价最大的一种做法，也会给攻击者一个精确的调试反馈。
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.device.guard")
public class DeviceGuardProperties {

    /**
     * 只观察不拦截。
     *
     * <p>默认 {@code true}，与设备令牌的 {@code mode: "off"} 是同一个思路：
     * <b>新的拦截能力默认不生效</b>，让它先在真实流量上跑一段时间。
     * 漏配的后果不对称 —— 默认不拦只是「防刷还没生效」，默认拦是「可能误伤一片」。
     */
    private boolean dryRun = true;

    /** 一台设备一天能登录几次（含失败）。30 对真人足够宽，对脚本已经很窄。 */
    private int maxLoginPerDay = 30;

    /**
     * 一台设备一小时能失败几次。
     *
     * <p>窗口比其它几项短：连续失败是<b>撞库的即时信号</b>，用一天的窗口反应太慢；
     * 而误伤的代价也小 —— 真人一小时内输错 10 次密码本来就该被拦一下。
     */
    private int maxFailPerHour = 10;

    /**
     * 一台设备一天能碰几个<b>不同的</b>会员号。
     *
     * <p>这是三条里<b>信号最强</b>的一条：一机多号是养号的必然特征，
     * 而正常用户换号登录是极少数（家人共用一台平板、换了新号）。
     *
     * <p>正因为强，误伤也最刺眼 —— 所以默认给到 3 而不是 1，
     * 并且方案里它的处置是「降档观察 + 打标」，不是封禁。
     */
    private int maxMembersPerDay = 3;


    private Duration dayWindow = Duration.ofDays(1);

    private Duration hourWindow = Duration.ofHours(1);

    public Duration dayWindow() {
        return dayWindow == null ? Duration.ofDays(1) : dayWindow;
    }

    public Duration hourWindow() {
        return hourWindow == null ? Duration.ofHours(1) : hourWindow;
    }
}
