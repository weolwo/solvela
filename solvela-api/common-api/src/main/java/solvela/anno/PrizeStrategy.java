package solvela.anno;

import solvela.enums.PrizeTypeEnum;

import java.lang.annotation.*;

/**
 * 奖品派发策略注解
 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PrizeStrategy {
    /**
     * 绑定的奖品类型，如 "SCORE", "BALANCE", "COUPON"
     */
    PrizeTypeEnum value();
}