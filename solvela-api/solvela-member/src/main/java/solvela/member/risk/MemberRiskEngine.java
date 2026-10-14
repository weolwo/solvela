package solvela.member.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;
import solvela.member.risk.config.RiskRuleRegistry;
import solvela.member.risk.rule.RiskHit;
import solvela.member.risk.rule.RiskRuleConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录风控引擎：按阶段跑规则，把命中交给 {@link RiskSession} 算结论。
 *
 * <h3>两个决策点</h3>
 * <ul>
 *   <li>{@link #evaluatePre}：验凭据<b>之前</b>。可以查账号，但账号信息<b>只能用来放宽</b> ——
 *       因为这时候的回答必须对「有这个号」和「查无此人」完全一样，否则就是一个账号枚举接口（方案 §3.1）；</li>
 *   <li>{@link #evaluatePost}：密码验对<b>之后</b>。这时才敢用设备信任、常用网络、活跃度这些账号信号。</li>
 * </ul>
 *
 * <h3>🔴 引擎出错 = 放行，但留痕</h3>
 * 单条规则抛异常 → 那条按未命中处理、记一条 error 命中；引擎整体出错 → 整个阶段放行。
 * 与现有各道闸一致（{@code LoginIpGuard.read} 读不出来当 0）：<b>风控挂了不能等于登录挂了</b>。
 * 而 error 命中的占比就是「这条规则是不是其实一直没在工作」的答案（方案 §8.2）。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberRiskEngine {

    private final RiskRuleRegistry registry;

    private final MemberRiskProperties properties;

    private final RiskEventRecorder recorder;

    /** 开一次登录尝试的账本 */
    public RiskSession open(RiskContext context) {
        return new RiskSession(context, properties, recorder, null);
    }

    /**
     * 开一次二次验证的账本。
     *
     * @param ref 签票的那次登录，写进 {@code ref_event_id} —— 「验码这一行」和「签票那一行」要能串起来
     */
    public RiskSession open(RiskContext context, RiskEventRef ref) {
        return new RiskSession(context, properties, recorder, ref);
    }

    public RiskDecision evaluatePre(RiskSession session) {
        return evaluate(session, MemberRiskPhaseEnum.PRE);
    }

    public RiskDecision evaluatePost(RiskSession session) {
        return evaluate(session, MemberRiskPhaseEnum.POST);
    }

    private RiskDecision evaluate(RiskSession session, MemberRiskPhaseEnum phase) {
        try {
            return session.settle(phase, runRules(session, phase));
        } catch (Exception e) {
            // 走到这里说明规则表快照也取不到了。放行，但要吵得够响
            log.error("【风控】{} 阶段整体执行失败，本次放行", phase, e);
            return session.current();
        }
    }

    private List<RiskSession.Evaluated> runRules(RiskSession session, MemberRiskPhaseEnum phase) {
        List<RiskSession.Evaluated> hits = new ArrayList<>();
        long budgetNanos = properties.featureBudget().toNanos();
        long slowNanos = properties.getSlowRuleWarn().toNanos();
        for (RiskRuleRegistry.Bound bound : registry.rules(phase)) {
            MemberRiskModeEnum mode = effectiveMode(bound);
            if (mode == MemberRiskModeEnum.OFF) {
                continue;
            }
            // 可信设备跳过：信任的定义与资产出口的二次验证共用一个答案（方案 §3.2）。
            // 不被跳过的是强规则和「不可能旅行」—— 可信不能洗白一个人工判过的东西，
            // 而一台 20 分钟内从深圳跳到纽约的「老设备」正是令牌被复制的样子
            if (bound.config().bypassByTrust() && session.context().deviceTrusted()) {
                continue;
            }
            if (session.engineNanos() >= budgetNanos && !bound.rule().alwaysEnforce()) {
                hits.add(errorHit(bound, mode, "超出风控时间预算，按未命中处理"));
                continue;
            }
            long started = System.nanoTime();
            try {
                RiskHit hit = bound.rule().evaluate(session.context(), bound.config());
                if (hit != null) {
                    hits.add(toEvaluated(bound, mode, hit));
                }
            } catch (Exception e) {
                log.error("【风控】规则 {} 执行失败，按未命中处理", bound.config().code(), e);
                hits.add(errorHit(bound, mode, "规则执行异常: " + e.getClass().getSimpleName()));
            } finally {
                long cost = System.nanoTime() - started;
                session.addEngineNanos(cost);
                if (cost >= slowNanos) {
                    log.warn("【风控】规则 {} 执行了 {} ms —— 特征查询变慢的第一个信号",
                            bound.config().code(), cost / 1_000_000L);
                }
            }
        }
        return hits;
    }

    /**
     * 这条规则此刻真正的档位。
     *
     * <p>总开关切到 {@code shadow} 时，{@code ENFORCE} 的规则一律降成陪跑 ——
     * 但 {@code alwaysEnforce()} 的那几条（全局滑块、黑名单、设备封禁）不降：
     * 保命开关是给「某条规则大面积误伤、又来不及定位是哪条」用的，
     * 它不该顺手把三条「已经确定要拦」的门也打开。
     */
    private MemberRiskModeEnum effectiveMode(RiskRuleRegistry.Bound bound) {
        MemberRiskModeEnum mode = bound.config().mode();
        if (mode == MemberRiskModeEnum.ENFORCE
                && properties.getMode() == MemberRiskProperties.Mode.SHADOW
                && !bound.rule().alwaysEnforce()) {
            return MemberRiskModeEnum.SHADOW;
        }
        return mode;
    }

    /**
     * 把规则返回的命中与它的配置折成一条账目。
     *
     * <h3>🔴 两条在这里强制的不变量</h3>
     * <ul>
     *   <li><b>打分规则永远不给处置</b>：哪怕它返回了一个 decision 也丢掉。打分都是「像不像」的统计信号，
     *       拿它拒绝一个其实是本人的登录，那个人不会来报障，只会不再打开（方案 §3.4）；</li>
     *   <li><b>强规则永远不加分</b>：它已经直接给出一档处置了，再加一次分等于同一件事算两遍。</li>
     * </ul>
     * 两者都打 WARN —— 这种错配是规则实现写错了，不该静默纠正完就算了。
     */
    private static RiskSession.Evaluated toEvaluated(RiskRuleRegistry.Bound bound, MemberRiskModeEnum mode,
                                                     RiskHit hit) {
        RiskRuleConfig config = bound.config();
        boolean scoring = config.ruleType() == MemberRiskRuleTypeEnum.SCORE;
        MemberRiskDecisionEnum decision = null;
        int score = 0;
        if (scoring) {
            if (hit.decision() != null) {
                log.warn("【风控】打分规则 {} 返回了处置 {}，已忽略", config.code(), hit.decision());
            }
            score = hit.score() != null ? hit.score() : config.score();
        } else {
            // 分级 / 矩阵规则自己决定是哪一档；固定处置的强规则用表里那一档
            decision = hit.decision() != null ? hit.decision() : config.directDecision();
            if (hit.score() != null) {
                log.warn("【风控】强规则 {} 返回了分值 {}，已忽略", config.code(), hit.score());
            }
        }
        return new RiskSession.Evaluated(config.code(), mode, decision, score, config.group(),
                hit.retryAfterSeconds(), hit.detail(), false, hit.actions());
    }

    private static RiskSession.Evaluated errorHit(RiskRuleRegistry.Bound bound, MemberRiskModeEnum mode,
                                                  String detail) {
        return new RiskSession.Evaluated(bound.config().code(), mode, null, 0, bound.config().group(),
                0L, detail, true, java.util.Set.of());
    }
}
