package net.lab1024.sa.risk.engine;

import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;

import java.math.BigDecimal;

/**
 * 第一梯队：本地参数校验规则（Order = 10）
 * 特点：纯内存计算，不消耗任何外部资源。如果这一步都过不去，根本没资格去查 Redis
 */
public class BasicLimitRiskFilter implements IRiskFilter {
    @Override
    public int getOrder() {
        return 10; // 第一优先级
    }

    @Override
    public RiskResult doFilter(RiskContext context) {
        PromotionConfig config = context.getConfig();
        BigDecimal applyAmount = context.getRequest().getPromotionValue();

        // 校验：单次最大金额兜底 (t_promotion_config.single_max_amount)
        if (config.getSingleMaxAmount().compareTo(BigDecimal.ZERO) > 0
                && applyAmount.compareTo(config.getSingleMaxAmount()) > 0) {
            return RiskResult.reject("SINGLE_MAX_AMOUNT_LIMIT", "单次发奖金额超限，触发系统兜底");
        }

        // 可以继续加单次最大数量(single_max_quota)等基础校验...

        return RiskResult.pass();
    }
}
