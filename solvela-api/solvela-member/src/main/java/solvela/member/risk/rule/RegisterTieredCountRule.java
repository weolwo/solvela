package solvela.member.risk.rule;

import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.feature.LoginCounters;
import solvela.member.risk.feature.RiskParams;
import tools.jackson.databind.JsonNode;

/**
 * 注册场景的分级计数规则。与登录那一套（{@link TieredCountRule}）的区别只有两条，但都是硬的。
 *
 * <h3>① 注册只有前置</h3>
 * 登录分前后两段是因为「密码对不对」把可用的信号分成了两半。注册没有这条分界线 ——
 * 验证码验过之后账号就建出来了，没有「验完再判一次」的位置。
 *
 * <h3>🔴 ② 处置只能是放行 / 限流 / 拒绝</h3>
 * 滑块和 OTP 在注册这里<b>没有落点</b>：滑块票由网关在「发码」那一步核销，
 * 注册接口本身收不到票；而注册已经验过一次验证码了，再要一道 OTP
 * 等于让同一个邮箱收两封信、证明同一件事。
 *
 * <p>所以参数校验直接拒绝配成滑块或 OTP ——
 * 不拒绝的话，后台把档位配成「滑块」之后这条规则会给出一个<b>没人能满足的处置</b>：
 * 表现是注册接口返回一个客户端不认识的错误，而规则页上那一行看着很正常。
 * 判据与 {@code LoginMatrixRule} 拒绝配成「冷却」同一个：
 * <b>一个此刻执行不了的处置，不如在保存那一刻就拦住。</b>
 *
 * @Date 2026-10-16
 */
public abstract class RegisterTieredCountRule extends TieredCountRule {

    protected RegisterTieredCountRule(LoginCounters counters) {
        super(counters);
    }

    @Override
    public final MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.REGISTER;
    }

    @Override
    public final MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public void validate(JsonNode params) {
        super.validate(params);
        // 与重置密码共用同一份校验：两边的理由一字不差（见 RiskParams.requireGateOnly）
        RiskParams.requireGateOnly(params, "注册");
    }
}
