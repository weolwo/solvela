package solvela.member.risk.rule;

import solvela.enums.MemberRiskPhaseEnum;
import solvela.member.risk.RiskContext;
import tools.jackson.databind.JsonNode;

/**
 * 一条风控规则。<b>逻辑在这里，参数、分值、档位在 {@code t_member_risk_rule}</b>。
 *
 * <h3>为什么规则写成类、不做 DSL</h3>
 * 规则写错的表现是「静默放行」或「误伤一片」，两种都不会报错。写成类能被编译器和单测钉住；
 * 后台可调的只是参数、档位、矩阵格 —— 那正好是需要频繁调的部分（方案 §10）。
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li>{@link #code()} 与表里的 {@code rule_code} 一一对应。<b>代码里有、表里没有 → 启动失败</b>
 *       （一条没有档位的规则，默认开或关都是错的）；</li>
 *   <li>没命中返回 null。<b>数据缺失 = 不判</b>（返回 null，不是抛异常）：那是常态；</li>
 *   <li>抛异常 = 特征取不到，引擎按未命中处理并记一条 error 命中 —— 那是故障。</li>
 *   <li>不要在规则里写库、发消息：陪跑中的规则也会执行。有副作用的动作由引擎按<b>生效的</b>结论统一做。</li>
 * </ul>
 *
 * @Date 2026-10-15
 */
public interface RiskRule {

    String code();

    MemberRiskPhaseEnum phase();

    /**
     * 总开关切到 shadow 时仍然生效。只有全局滑块、黑名单、设备封禁是 true ——
     * 那三条是「已经确定要拦」的东西，保命开关不该把它们也放掉。
     */
    default boolean alwaysEnforce() {
        return false;
    }

    /**
     * 校验参数形状，不合法抛 {@link IllegalArgumentException}。
     *
     * <p>加载时与后台保存时都会调用同一个方法 —— 存进去一个读不出来的参数，等于把那条规则静默关掉。
     */
    default void validate(JsonNode params) {
    }

    RiskHit evaluate(RiskContext ctx, RiskRuleConfig config);
}
