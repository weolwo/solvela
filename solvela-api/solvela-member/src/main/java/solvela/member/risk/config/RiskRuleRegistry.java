package solvela.member.risk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import solvela.base.json.JsonUtils;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;
import solvela.member.MemberRiskRule;
import solvela.member.risk.MemberRiskProperties;
import solvela.member.risk.dao.MemberRiskRuleDao;
import solvela.member.risk.rule.RiskRule;
import solvela.member.risk.rule.RiskRuleConfig;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 规则表 ↔ 代码的对照，以及规则配置的本地缓存。
 *
 * <h3>🔴 启动时双向校验</h3>
 * <ul>
 *   <li><b>代码里有、表里没有 → 启动失败</b>。一条没有档位的规则，默认开或关都是错的；
 *       而这种情况只会出现在「发了新版本、忘了加种子数据」时，越早炸越好；</li>
 *   <li><b>表里有、代码里没有 → WARN，当作关闭</b>。下线一条规则时先删代码、后删数据，中间那段时间不该起不来。</li>
 * </ul>
 *
 * <h3>参数读不出来的规则当作关闭，并打 ERROR</h3>
 * 不让它启动失败：参数是后台可改的东西，后台保存时已经用同一个 {@code validate} 校验过，
 * 走到这里只可能是有人直接改了库。为一条规则让整个登录起不来，代价不对称。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
public class RiskRuleRegistry implements SmartInitializingSingleton {

    private final MemberRiskRuleDao ruleDao;

    private final Map<String, RiskRule> rulesByCode;

    private final PeriodicSnapshot<Snapshot> snapshot;

    public RiskRuleRegistry(MemberRiskRuleDao ruleDao,
                            ObjectProvider<RiskRule> rules,
                            MemberRiskProperties properties) {
        this.ruleDao = ruleDao;
        this.rulesByCode = indexByCode(rules.orderedStream().toList());
        this.snapshot = new PeriodicSnapshot<>("规则表", this::load, properties.cacheRefresh());
    }

    /** 一条可执行的规则：实现 + 此刻的配置 */
    public record Bound(RiskRule rule, RiskRuleConfig config) {
    }

    /**
     * @param byScenario 场景 → 阶段 → 要执行的规则。<b>两层都要</b>：
     *                   少了场景，注册的规则会跑在登录上；少了阶段，前置规则会在验密码之后执行
     */
    record Snapshot(Map<MemberRiskEventTypeEnum, Map<MemberRiskPhaseEnum, List<Bound>>> byScenario,
                    Map<String, RiskRuleConfig> byCode) {
    }

    @Override
    public void afterSingletonsInstantiated() {
        Snapshot loaded = snapshot.loadNow();
        Set<String> missing = new TreeSet<>(rulesByCode.keySet());
        missing.removeAll(loaded.byCode().keySet());
        // 表里有但没加载成功的（参数坏了）不算「缺」：它们已经单独打过 ERROR
        missing.removeAll(loadedButBroken);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("【风控】规则表缺少这些规则的种子数据，拒绝启动: " + missing
                    + "。见 数据库SQL脚本/mysql/data-baseline.sql 的 t_member_risk_rule");
        }
        loaded.byScenario().forEach((eventType, byPhase) -> {
            int pre = byPhase.get(MemberRiskPhaseEnum.PRE).size();
            int post = byPhase.get(MemberRiskPhaseEnum.POST).size();
            if (pre + post > 0) {
                log.info("【风控】规则加载完成：{} —— 前置 {} 条，后置 {} 条", eventType.getDesc(), pre, post);
            }
        });
    }

    /** 某个场景的某个阶段此刻要执行的规则（不含关闭的），按表里的 id 顺序 */
    public List<Bound> rules(MemberRiskEventTypeEnum eventType, MemberRiskPhaseEnum phase) {
        Map<MemberRiskPhaseEnum, List<Bound>> byPhase = snapshot.get().byScenario().get(eventType);
        if (byPhase == null) {
            // 这个场景没有任何规则（比如二次验证那一步，它刻意不跑规则）
            return List.of();
        }
        List<Bound> rules = byPhase.get(phase);
        return rules == null ? List.of() : rules;
    }

    /** 某条规则此刻的配置；不存在（或已关闭）返回 null */
    public RiskRuleConfig config(String code) {
        return snapshot.get().byCode().get(code);
    }

    /**
     * 用这条规则<b>自己的</b> {@code validate} 校验一份参数；不合法抛 {@link IllegalArgumentException}。
     *
     * <h3>🔴 后台保存与加载必须走同一个方法</h3>
     * 只在加载时校验的话，一个读不出来的参数能被存进去 —— 然后那条规则在下一次缓存刷新时
     * <b>静默关闭</b>，而页面上看着保存成功、列表里也有那一行。
     * 「形状对不对」只有规则自己知道（分级要 tiers 递增、矩阵不许配成拒绝、机房关键字不能为空……）。
     */
    public void validateParams(String code, String paramsJson) {
        RiskRule impl = rulesByCode.get(code);
        if (impl == null) {
            throw new IllegalArgumentException("代码里没有这条规则，改不了它的参数: " + code);
        }
        JsonNode params;
        try {
            params = SolvelaStringUtil.isBlank(paramsJson)
                    ? JsonUtils.getMapper().createObjectNode()
                    : JsonUtils.getMapper().readTree(paramsJson);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("参数不是合法的 JSON: " + e.getMessage(), e);
        }
        if (!params.isObject()) {
            throw new IllegalArgumentException("参数必须是一个 JSON 对象");
        }
        impl.validate(params);
    }

    /** 上一次加载时参数读不出来的规则。只用于启动校验时把它们和「缺种子」区分开 */
    private volatile Set<String> loadedButBroken = Set.of();

    private Snapshot load() {
        Map<MemberRiskEventTypeEnum, Map<MemberRiskPhaseEnum, List<Bound>>> byScenario =
                new EnumMap<>(MemberRiskEventTypeEnum.class);
        for (MemberRiskEventTypeEnum eventType : MemberRiskEventTypeEnum.values()) {
            Map<MemberRiskPhaseEnum, List<Bound>> byPhase = new EnumMap<>(MemberRiskPhaseEnum.class);
            for (MemberRiskPhaseEnum phase : MemberRiskPhaseEnum.values()) {
                byPhase.put(phase, new ArrayList<>());
            }
            byScenario.put(eventType, byPhase);
        }
        Map<String, RiskRuleConfig> byCode = new LinkedHashMap<>();
        Set<String> broken = new TreeSet<>();
        List<MemberRiskRule> rows = ruleDao.selectList(null);
        rows.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        for (MemberRiskRule row : rows) {
            RiskRule impl = rulesByCode.get(row.getRuleCode());
            if (impl == null) {
                log.warn("【风控】规则表里有 {}，但代码里没有这条规则，当作关闭", row.getRuleCode());
                continue;
            }
            RiskRuleConfig config;
            try {
                config = toConfig(row, impl);
            } catch (RuntimeException e) {
                broken.add(row.getRuleCode());
                log.error("【风控】规则 {} 的配置读不出来，当作关闭：{}", row.getRuleCode(), e.getMessage());
                continue;
            }
            byCode.put(config.code(), config);
            if (config.mode() != MemberRiskModeEnum.OFF) {
                byScenario.get(config.eventType()).get(config.phase()).add(new Bound(impl, config));
            }
        }
        byScenario.replaceAll((eventType, byPhase) -> {
            byPhase.replaceAll((phase, list) -> Collections.unmodifiableList(list));
            return Collections.unmodifiableMap(byPhase);
        });
        loadedButBroken = Collections.unmodifiableSet(broken);
        return new Snapshot(Collections.unmodifiableMap(byScenario), Collections.unmodifiableMap(byCode));
    }

    /**
     * 行 → 配置。<b>阶段与场景都以代码为准，并与表核对</b>：
     * <ul>
     *   <li>一条前置规则被人在表里改成后置，它会在验密码之后才执行 ——
     *       前置的「有号没号回答一致」就不成立了；</li>
     *   <li>一条登录规则被改成注册场景，它会在一个压根没有密码、没有设备信任的流程里执行 ——
     *       而那不会报错，表现是「这条规则再也不命中」或者「它开始误伤注册」。</li>
     * </ul>
     * 两种都让这一行配置读不出来（当作关闭并打错误日志），启动校验那一步会把它算成「坏行」。
     */
    static RiskRuleConfig toConfig(MemberRiskRule row, RiskRule impl) {
        if (row.getPhase() != impl.phase()) {
            throw new IllegalArgumentException("阶段与代码不一致：表里是 " + row.getPhase() + "，代码是 " + impl.phase());
        }
        if (row.getEventType() != impl.eventType()) {
            throw new IllegalArgumentException("适用场景与代码不一致：表里是 " + row.getEventType()
                    + "，代码是 " + impl.eventType());
        }
        if (row.getMode() == null || row.getRuleType() == null) {
            throw new IllegalArgumentException("mode / rule_type 为空");
        }
        if (row.getRuleType() == MemberRiskRuleTypeEnum.DIRECT && row.getDirectDecision() == null) {
            throw new IllegalArgumentException("固定处置的强规则缺 direct_decision");
        }
        /*
         * 🔴 注册场景只能配放行 / 限流 / 拒绝。
         *
         * 滑块与 OTP 在注册这条链路上【没有落点】（滑块票在发码那一步核销，注册本身已经验过一次码），
         * 配上去的后果不是报错，而是注册接口返回一个客户端不认识的结果 ——
         * 而规则页上那一行看着完全正常。
         *
         * 分级规则的档位由各规则自己的 validate 拦（见 RegisterTieredCountRule），
         * 这里拦的是固定处置那一列：它在后台改不了，所以只可能是有人直接动了库。
         */
        if (row.getEventType() == MemberRiskEventTypeEnum.REGISTER && row.getDirectDecision() != null
                && !registerDecisionAllowed(row.getDirectDecision())) {
            throw new IllegalArgumentException("注册场景的固定处置只能是放行 / 限流 / 拒绝，表里是 "
                    + row.getDirectDecision());
        }
        JsonNode params = SolvelaStringUtil.isBlank(row.getParams())
                ? JsonUtils.getMapper().createObjectNode()
                : JsonUtils.getMapper().readTree(row.getParams());
        impl.validate(params);
        return new RiskRuleConfig(row.getRuleCode(), row.getRuleName(), row.getEventType(),
                row.getPhase(), row.getRuleType(),
                row.getDirectDecision(), row.getScore() == null ? 0 : row.getScore(), row.getRuleGroup(),
                row.getMode(), Integer.valueOf(1).equals(row.getBypassByTrust()), params);
    }

    /** 注册场景允许的固定处置。与 {@code RegisterTieredCountRule.validate} 拦的是同一件事的两种形状 */
    private static boolean registerDecisionAllowed(MemberRiskDecisionEnum decision) {
        return decision == MemberRiskDecisionEnum.PASS
                || decision == MemberRiskDecisionEnum.LIMIT
                || decision == MemberRiskDecisionEnum.DENY;
    }

    private static Map<String, RiskRule> indexByCode(
            List<RiskRule> rules) {
        Map<String, RiskRule> byCode = new LinkedHashMap<>();
        for (RiskRule rule : rules) {
            if (byCode.put(rule.code(), rule) != null) {
                throw new IllegalStateException("【风控】两条规则用了同一个编码: " + rule.code());
            }
        }
        return Collections.unmodifiableMap(byCode);
    }
}
