package solvela.member.risk.rule;

import solvela.enums.MemberRiskDecisionEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.OldDeviceExemption;

/**
 * IP 维度的分级计数规则。与设备维度的区别<b>只有一条</b>：限流档受「老设备豁免」约束（方案 §4.1）。
 *
 * <h3>为什么单独一层，而不是在基类里加个开关</h3>
 * 「这台设备很老」对设备维度的规则<b>恰恰不是免责理由</b> ——
 * {@code DEVICE_FANOUT}、{@code DEVICE_FAIL_LIMIT} 挡的正是「这台设备在挨个试号」，
 * 一台用了半年的设备开始挨个试号，比一台新设备这么干更值得警惕。
 *
 * <p>做成基类上的一个布尔参数，迟早有人给设备规则也配上 true（它看起来只是「放松一点」）。
 * 分成两层的话，那件事要先改继承关系 —— 而那一步会让人读一遍这段注释。
 *
 * @Date 2026-10-16
 */
public abstract class IpTieredCountRule extends TieredCountRule {

    private final OldDeviceExemption exemption;

    protected IpTieredCountRule(LoginCounters counters, OldDeviceExemption exemption) {
        super(counters);
        this.exemption = exemption;
    }

    /**
     * 老设备免限流：限流及以上的那几档当作没配，滑块档照常。
     *
     * <p>🔴 计数不受影响 —— 豁免压的是<b>处置</b>，不是<b>观察</b>。
     * 不记的话，这个 IP 的真实量级会被老设备的流量抹平，而那正是攻击时最该看清的数。
     */
    @Override
    protected MemberRiskDecisionEnum limitCeiling(RiskContext ctx) {
        return exemption.exempt(ctx) ? MemberRiskDecisionEnum.LIMIT : null;
    }
}
