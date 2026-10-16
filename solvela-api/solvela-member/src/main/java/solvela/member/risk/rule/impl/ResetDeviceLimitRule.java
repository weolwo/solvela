package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.rule.ResetTieredCountRule;

/**
 * 单设备在一个窗口里提交了几次重置密码。
 *
 * <h3>它比 IP 那一维硬，但在这个场景里更容易误伤</h3>
 * 一家人共用一台平板、一个人帮三位长辈找回账号 —— 都是真事。
 * 所以阈值按「天」算而且给得宽（默认 5），命中也只是限流，<b>不降设备档</b>：
 * 把一台正在帮人找回账号的设备降进观察档，等于让它接下来的登录也要多验一道码。
 *
 * <p>⚠️ 与登录那边的 {@code DEVICE_FAIL} 是两件事：验证码输错时主干仍然会调
 * {@code DeviceGuard.recordLoginFailure}（那是跨场景的信号，刻意保留）——
 * 本规则数的是「提交了几次」，不是「错了几次」。
 *
 * @Date 2026-10-16
 */
@Component
public class ResetDeviceLimitRule extends ResetTieredCountRule {

    public ResetDeviceLimitRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "RESET_DEVICE_LIMIT";
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.RESET_DEVICE;
    }

    @Override
    protected String dimensionValue(RiskContext ctx) {
        return ctx.getDeviceId();
    }

    @Override
    protected boolean countsThisRequest() {
        return true;
    }

    @Override
    protected String dimensionName() {
        return "本设备重置密码";
    }
}
