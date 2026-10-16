package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.DeviceMemberFanoutRule;

import java.util.Set;

/**
 * 一台设备在窗口内给<b>几个不同会员</b>换过联系方式。
 *
 * <h3>它抓的是「批量接管」那一步</h3>
 * 把联系方式换成自己的，是接管一个账号<b>最后、也最彻底</b>的一下（换完之后原主连找回
 * 密码都走不通，只剩 7 天冷静期在挡）。一台设备一天替三个人换，不是家人互助的样子。
 *
 * <h3>🔴 这里只能限流 / 拒绝，不能要求核身</h3>
 * 换绑这条链路上<b>没有可以追加的核身</b>：换绑本身已经要「新地址的码」，
 * 而「证明你是原主」那一步对<b>首次绑定</b>压根不存在（没有旧联系方式可问）——
 * 偏偏首次绑定才是那条接管链要用的口子。所以这条规则给不出 OTP。
 *
 * <h3>先陪跑</h3>
 * 阈值是新拍的，而处置是硬拦 —— 按 §12.6 那条判据（人工签过字的、迁过来的才敢直接生效），
 * 它从陪跑起步。等看板上攒出「会改变多少次结论」再切。
 *
 * @Date 2026-10-16
 */
@Component
public class BindDeviceFanoutRule extends DeviceMemberFanoutRule {

    public BindDeviceFanoutRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public String code() {
        return "BIND_DEVICE_FANOUT";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.CONTACT_BIND;
    }

    @Override
    protected LoginCounters.Counter counter() {
        return LoginCounters.Counter.BIND_DEVICE_MEMBER;
    }

    @Override
    protected Set<MemberRiskDecisionEnum> allowedDecisions() {
        return RiskParams.GATE_ONLY;
    }

    @Override
    protected String whyLimited() {
        return "换绑这条链路上没有可以追加的核身：它本来就要新地址的码，"
                + "而「证明你是原主」那一步对首次绑定压根不存在";
    }

    @Override
    protected String operationName() {
        return "换过联系方式";
    }
}
