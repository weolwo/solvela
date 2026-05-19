package net.lab1024.sa.risk.engine;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class RiskResult {
    private boolean passed;
    private String reason;
    private String ruleCode; // 命中的规则编码，用于打日志和数仓分析

    public static RiskResult pass() {
        return new RiskResult(true, null, null);
    }

    public static RiskResult reject(String ruleCode, String reason) {
        return new RiskResult(false, reason, ruleCode);
    }
}
