package solvela.member.register;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 注册配置。<b>只剩一项</b>。
 *
 * <h3>限频在 2026-10-16 搬去了风控规则表</h3>
 * 原来这里有 {@code window} 与 {@code maxAttemptsPerIp}（1 小时 / 10 次）。
 * 它们现在是 {@code REG_IP_LIMIT} 规则的 params —— 搬过去是为了
 * <b>改阈值不用发版</b>，而注册这条链路恰恰是最需要当场调松的：
 * 误伤一个注册中的人，他不会来投诉，他只是走了。
 *
 * <p>留在这里的只有「手机号注册要不要验码」：那不是一个松紧刻度，
 * 而是一个<b>要有人签字</b>的决定（见下）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.register")
public class MemberRegisterProperties {

    /**
     * 手机号注册<b>是否必须验短信验证码</b>。
     *
     * <h3>🔴 默认 true，而全仓还没接短信服务商 —— 这是刻意的</h3>
     * 默认值应当描述<b>正确的世界</b>，而不是迁就当前的缺口。
     * 配成 false 是一个<b>需要有人明确按下去</b>的决定，而不是一个谁也没注意到的现状。
     *
     * <p>配 false 的后果要说清楚：<b>任何人都能拿别人的手机号建账号</b>，
     * 而 {@code uk_mbr_phone_hash} 是唯一约束 —— 号被占了，真机主就再也注册不了。
     * 唯一的缓解是 IP 与设备限频，它们只能压速率，<b>拦不住定向占号</b>
     * （占一个号只需要成功一次）。
     *
     * <p>过渡期建议：dev / test 配 true 并把 {@code sms-transport} 配成 LOG，
     * 链路照样跑得通；生产在接上厂商之前如果必须放行注册，就把这一项配成 false，
     * <b>并且知道自己在放行什么</b>。
     */
    private boolean phoneCodeRequired = true;
}
