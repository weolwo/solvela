package solvela.risk.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.risk.PromotionConfig;

import java.math.BigDecimal;

/**
 * 第一梯队：本地参数校验规则（Order = 10）
 * 特点：纯内存计算，不消耗任何外部资源。如果这一步都过不去，根本没资格去查 Redis
 *
 * <h3>🔴 这个类曾经整整一段时间不在风控链里</h3>
 * {@link RiskChainEngine} 是靠 {@code @Resource List<IRiskFilter>} 注入的 —— 它只收 Spring Bean。
 * 本类原先<b>没有任何构造型注解</b>，于是从来没被注册过：
 * 启动日志那句「已加载 N 个风控规则节点」是 2 而不是 3，而 {@code single_max_amount}
 * 这道「防一次发出天量」的兜底闸门<b>是虚的</b> —— 运营在表单里填了值、页面也保存成功，
 * 只是运行时没有任何一行代码去读它。
 *
 * <p>与「策略工厂拿 String 查枚举键的 Map」是同一类事故：编译通过、无异常、无日志，
 * 只有对着下游数据逐条比对才看得出来。新增 {@code IRiskFilter} 实现时，
 * <b>第一件事是确认它是不是 Bean</b>。
 */
@Slf4j
@Service
public class BasicLimitRiskFilter implements IRiskFilter {

    @Override
    public int getOrder() {
        return 10; // 第一优先级
    }

    @Override
    public RiskResult doFilter(RiskContext context) {
        PromotionConfig config = context.getConfig();
        BigDecimal applyAmount = context.getRequest().getAmount();

        // 校验：单次最大金额兜底 (t_promotion_config.single_max_amount)
        // 0 表示不设兜底 —— DDL 默认值就是 0，存量配置绝大多数没填，
        // 所以这道闸门补进链路之后不会突然拦住谁
        if (config.getSingleMaxAmount() != null
                && config.getSingleMaxAmount().compareTo(BigDecimal.ZERO) > 0
                && applyAmount != null
                && applyAmount.compareTo(config.getSingleMaxAmount()) > 0) {
            return RiskResult.reject(RiskBlockCode.SINGLE_MAX_AMOUNT_LIMIT, "单次发奖金额超限，触发系统兜底");
        }

        // 校验：单次最大数量兜底 (t_promotion_config.single_max_quota)
        // 与金额那道同构，防的是券/实物「一次发出 999 张」这类配置事故。
        // 补上之前这个字段是表单里的必填项却没有任何消费方，填了等于没填。
        Integer singleMaxQuota = config.getSingleMaxQuota();
        Integer applyQuantity = context.getRequest().getQuantity();
        if (singleMaxQuota != null && singleMaxQuota > 0
                && applyQuantity != null && applyQuantity > singleMaxQuota) {
            return RiskResult.reject(RiskBlockCode.SINGLE_MAX_QUOTA_LIMIT, "单次发奖数量超限，触发系统兜底");
        }

        return RiskResult.pass();
    }
}
