package net.lab1024.sa.risk.engine;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class RiskResult {
    private boolean passed;
    private String reason;
    /**
     * 命中的规则编码，用于打日志、落 {@code t_proposal_record.risk_code} 与数仓分析。
     * 取值收敛在 {@link RiskBlockCode}：文案会改、会带上具体数值，编码不会。
     */
    private String ruleCode;

    public static RiskResult pass() {
        return new RiskResult(true, null, null);
    }

    /**
     * 拦截。<b>新增拦截规则一律走这个重载</b> —— 编码进枚举，漏斗才聚得起来。
     */
    public static RiskResult reject(RiskBlockCode ruleCode, String reason) {
        return new RiskResult(false, reason, ruleCode.getValue());
    }

    /**
     * 拦截（裸字符串编码）。
     *
     * <p>⚠️ 保留只为兼容外部可能存在的调用，<b>不要用它写新规则</b>：
     * 随手起一个不在 {@link RiskBlockCode} 里的编码，那类拦截在提案漏斗上会落进
     * 「未归类」，看起来像没发生过。
     */
    public static RiskResult reject(String ruleCode, String reason) {
        return new RiskResult(false, reason, ruleCode);
    }
}
