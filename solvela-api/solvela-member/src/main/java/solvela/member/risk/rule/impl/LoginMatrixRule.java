package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.Member;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskIpFamiliarity;
import solvela.member.risk.feature.DeviceTrustProvider;
import solvela.member.risk.feature.Familiarity;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 后置矩阵：<b>设备信任 × IP 熟悉度 × 活跃度</b> → 放行还是要一道验证码。这是整套风控的主模型。
 *
 * <h3>为什么用矩阵，不用纯打分</h3>
 * 每一格都能用一句人话说出来（「新设备 + 陌生网络，所以要验证码」）——
 * 客服能直接念给用户听，运营能直接在后台改某一格。纯打分的解释是「你得了 45 分」，
 * 没人知道该改哪里。打分留给那些单独不足以下结论的弱信号（方案 §2）。
 *
 * <h3>四格</h3>
 * <pre>
 *   可信设备                     → 放行（沉睡回来也放行，见下）
 *   新设备 + 熟悉网络 + 活跃      → 放行（新设备提醒照发）
 *   新设备 + 熟悉网络 + 沉睡      → OTP
 *   新设备 + 陌生网络            → OTP
 * </pre>
 * 取值在 {@code params.cells} 里，后台可改 —— 想让「可信 + 沉睡」也验一道，改那一格就行。
 *
 * <h3>🔴 可信设备沉睡回来为什么放行</h3>
 * 一台老设备隔了一个月再来，大概率是本人。它要是被复制了，由 {@code IMPOSSIBLE_TRAVEL}
 * 去抓（那条规则<b>不被可信设备绕过</b>）。
 *
 * <h3>🔴 「沉睡」按最后活跃算，不按最后登录算</h3>
 * 会话令牌 30 天有效且不续期，一个每天都在用的人每 30 天才登录一次 ——
 * 按登录算会把每个活跃用户每月判一次沉睡。见 {@code MemberActivityService}。
 *
 * @Date 2026-10-15
 */
@Component
public class LoginMatrixRule implements RiskRule {

    private static final int DEFAULT_DORMANT_DAYS = 30;

    private final DeviceTrustProvider trustProvider;

    private final Familiarity familiarity;

    public LoginMatrixRule(DeviceTrustProvider trustProvider, Familiarity familiarity) {
        this.trustProvider = trustProvider;
        this.familiarity = familiarity;
    }

    @Override
    public String code() {
        return "LOGIN_MATRIX";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.POST;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.positiveInt(params, "dormantDays", DEFAULT_DORMANT_DAYS);
        for (Cell cell : Cell.values()) {
            decisionOf(params, cell);
        }
    }

    /** 矩阵的四格。编码是 {@code params.cells} 里的键 */
    private enum Cell {
        TRUSTED,
        NEW_FAMILIAR_ACTIVE,
        NEW_FAMILIAR_DORMANT,
        NEW_UNFAMILIAR,
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (!ctx.memberExists()) {
            // 走不到：后置只在密码验对之后跑。留着是因为「验对了」依赖另一处代码写对
            return null;
        }
        boolean trusted = trustProvider.of(ctx).trusted();
        RiskIpFamiliarity ip = familiarity.of(ctx, config.params());
        boolean dormant = dormant(ctx, config.params());
        ctx.dormant(dormant);

        Cell cell = trusted ? Cell.TRUSTED
                : ip.familiar()
                ? (dormant ? Cell.NEW_FAMILIAR_DORMANT : Cell.NEW_FAMILIAR_ACTIVE)
                : Cell.NEW_UNFAMILIAR;

        MemberRiskDecisionEnum decision = decisionOf(config.params(), cell);
        if (decision == null || decision == MemberRiskDecisionEnum.PASS) {
            return null;
        }
        return RiskHit.decide(decision, describe(trusted, ip, dormant));
    }

    /**
     * 这个账号是不是沉睡。
     *
     * <p>{@code last_active_time} 为空时退回「注册时间」—— 那一列从 2026-10-15 起由注册写入，
     * 为空只可能是更早建的号。<b>不退回「最后登录时间」</b>：那正是这一列要取代的东西。
     */
    private boolean dormant(RiskContext ctx, JsonNode params) {
        Member member = ctx.getMember();
        LocalDateTime active = member.getLastActiveTime() != null
                ? member.getLastActiveTime()
                : member.getCreateTime();
        if (active == null) {
            // 两个都没有：判不了。不判比乱判好 —— 乱判的方向是「人人都沉睡、人人都要验码」
            return false;
        }
        Duration threshold = Duration.ofDays(RiskParams.positiveInt(params, "dormantDays", DEFAULT_DORMANT_DAYS));
        return active.plus(threshold).isBefore(LocalDateTime.now());
    }

    private static MemberRiskDecisionEnum decisionOf(JsonNode params, Cell cell) {
        JsonNode node = params.path("cells").path(cell.name());
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException("cells." + cell.name() + " 必须是处置的数值，实际是: " + node);
        }
        MemberRiskDecisionEnum decision = MemberRiskDecisionEnum.ofValue(node.asInt());
        if (decision.atLeast(MemberRiskDecisionEnum.COOLDOWN)) {
            // 🔴 矩阵只在「放行 / 滑块 / OTP」之间选。配成限流或拒绝的话，
            //    一格配错就会把一整类正常用户挡在门外，而那类用户不会来报障
            throw new IllegalArgumentException("cells." + cell.name()
                    + " 只能配到 OTP 为止，不能配成冷却 / 限流 / 拒绝，实际是: " + decision);
        }
        return decision;
    }

    private static String describe(boolean trusted, RiskIpFamiliarity ip, boolean dormant) {
        return (trusted ? "可信设备" : "新设备")
                + " + " + (ip == RiskIpFamiliarity.STRICT ? "常用网段"
                : ip == RiskIpFamiliarity.LOOSE ? "同城同运营商" : "陌生网络")
                + (dormant ? " + 沉睡账号" : "");
    }
}
