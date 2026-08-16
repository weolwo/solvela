package net.lab1024.sa.risk.engine;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 第三梯队：全局预算水位管控（Order = 30）
 * 特点：防资金超发的最底线。只有当请求确实没问题了，才来碰最核心的预算数据
 */
@Slf4j
@Service
public class GlobalBudgetRiskFilter implements IRiskFilter {

    @Override
    public int getOrder() {
        return 30; // 第三优先级
    }

    @Override
    public RiskResult doFilter(RiskContext context) {
        PromotionConfig config = context.getConfig();
        BigDecimal applyAmount = context.getRequest().getAmount();

        // 如果配置了 -1 (不限预算)，直接放行
        if (config.getTotalAmount().compareTo(BigDecimal.valueOf(-1)) == 0) {
            return RiskResult.pass();
        }

        // 校验：剩余预算是否充足
        // 注意：在大并发下，这里只是一个“弱校验”。
        // 真正的硬限流应该在提案通过后、实际动账前，通过 SQL 乐观锁拦截：
        // UPDATE t_promotion_config SET used_amount = used_amount + x WHERE (total_amount - used_amount) >= x
        BigDecimal remainAmount = config.getTotalAmount().subtract(config.getUsedAmount());

        if (applyAmount.compareTo(remainAmount) > 0) {
            return RiskResult.reject(RiskBlockCode.GLOBAL_BUDGET_LIMIT, "活动过于火爆，当前奖项预算已耗尽");
        }

        return RiskResult.pass();
    }
}
