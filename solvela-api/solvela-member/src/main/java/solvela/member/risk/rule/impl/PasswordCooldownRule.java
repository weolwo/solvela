package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.api.MemberLoginType;
import solvela.member.risk.RiskContext;
import solvela.member.risk.feature.DeviceTrustProvider;
import solvela.member.risk.feature.PasswordCooldownStore;
import solvela.member.risk.feature.RiskParams;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

/**
 * 密码连续错误的阶梯冷却。<b>取代改造前的账号锁</b>（30 分钟错 5 次、锁 30 分钟）。
 *
 * <h3>本规则只判「还在冷却中吗」</h3>
 * 计数发生在密码比对失败<b>之后</b>，由认证主干调 {@link PasswordCooldownStore#recordFailure} 完成。
 * 规则在前置跑，那时还没验密码 —— 它要做的只是「上一次攒下来的冷却还没过，就别再给试的机会」。
 *
 * <p>🔴 冷却期间<b>不验密码、不加计数</b>：不然被冷却的人一直点，每一次都把计数往上推，
 * 冷却只会越来越长，用户永远等不到解除（这个坑改造前的账号锁踩过，所以它有一段
 * 「已经在限制中就不再叠加」的代码）。
 *
 * <h3>为什么前两次不冷却</h3>
 * 手滑输错一次就要等一分钟，而第一次输错本身不构成威胁 —— 前面已经挡了一道滑块。
 * {@code free} 是参数，要从第 1 次就开始算改一个数就行。
 *
 * <h3>只拦密码登录</h3>
 * 邮箱验证码登录不进这条规则：它的凭据是一次性的码，码自己有「错 5 次作废」，不需要第二道。
 * 这也是被恶意冷却的人还有路可走的那条路。
 *
 * @Date 2026-10-15
 */
@Component
public class PasswordCooldownRule implements RiskRule {

    private static final int DEFAULT_FREE = 2;

    private static final Duration DEFAULT_IDLE_RESET = Duration.ofHours(24);

    private final PasswordCooldownStore store;

    private final DeviceTrustProvider trustProvider;

    public PasswordCooldownRule(PasswordCooldownStore store, DeviceTrustProvider trustProvider) {
        this.store = store;
        this.trustProvider = trustProvider;
    }

    @Override
    public String code() {
        return "PWD_COOLDOWN";
    }

    @Override
    public MemberRiskPhaseEnum phase() {
        return MemberRiskPhaseEnum.PRE;
    }

    @Override
    public void validate(JsonNode params) {
        RiskParams.positiveInt(params, "free", DEFAULT_FREE);
        List<Duration> steps = steps(params);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps 不能为空");
        }
        for (int i = 1; i < steps.size(); i++) {
            if (steps.get(i).compareTo(steps.get(i - 1)) < 0) {
                throw new IllegalArgumentException("steps 必须越来越长，否则错得越多反而冷却越短: "
                        + steps.get(i - 1) + " → " + steps.get(i));
            }
        }
        RiskParams.duration(params, "idleReset", DEFAULT_IDLE_RESET);
        RiskParams.duration(params, "recordFrom", Duration.ofMinutes(60));
    }

    @Override
    public RiskHit evaluate(RiskContext ctx, RiskRuleConfig config) {
        if (ctx.getLoginType() == MemberLoginType.EMAIL_CODE || ctx.getIdentityHashHex() == null) {
            return null;
        }
        // 🔴 可信桶只对存在的账号存在，所以攻击者永远落在共用桶 —— 账号信息只用来放宽（方案 §3.1）
        String bucket = store.bucket(ctx.getDeviceId(), trustProvider.of(ctx).trusted());
        long remaining = store.remainingSeconds(ctx.getIdentityHashHex(), bucket);
        if (remaining <= 0) {
            return null;
        }
        RiskHit hit = RiskHit.decide(MemberRiskDecisionEnum.COOLDOWN, remaining,
                "密码连续错误 " + store.failures(ctx.getIdentityHashHex(), bucket) + " 次，冷却中");
        /*
         * 长冷却要在后台可见、可人工解除，所以请求 RECORD_LIMIT 落一行 t_member_operation_limit。
         *
         * 🔴 只有长的那几档才落：60 秒那一档也落库的话，那张表会被手滑的人刷满，
         *    而客服要找的恰恰是「被锁了很久」的那几个人。
         *
         * ⚠️ 按【剩余时间】判，不是按「这一次罚了多久」—— 规则在前置跑，那时它只知道剩多久。
         *    代价是一个 24 小时的冷却快结束时不再请求落库，而那一行早就落过了。
         */
        if (remaining >= recordFrom(config.params()).toSeconds()) {
            return hit.withActions(solvela.member.risk.RiskAction.RECORD_LIMIT);
        }
        return hit;
    }

    /** 认证主干记失败时也要这几个参数，所以解析逻辑放这里公用 */
    public static List<Duration> steps(JsonNode params) {
        JsonNode node = params.path("steps");
        if (!node.isArray()) {
            return List.of();
        }
        List<Duration> steps = new java.util.ArrayList<>(node.size());
        for (JsonNode item : node) {
            steps.add(RiskParams.parseDuration(item.asString(), "steps[]"));
        }
        return List.copyOf(steps);
    }

    public static int free(JsonNode params) {
        return RiskParams.positiveInt(params, "free", DEFAULT_FREE);
    }

    public static Duration idleReset(JsonNode params) {
        return RiskParams.duration(params, "idleReset", DEFAULT_IDLE_RESET);
    }

    /** 冷却达到它才往 {@code t_member_operation_limit} 写一行（短冷却落库会把那张表刷满） */
    public static Duration recordFrom(JsonNode params) {
        return RiskParams.duration(params, "recordFrom", Duration.ofMinutes(60));
    }
}
