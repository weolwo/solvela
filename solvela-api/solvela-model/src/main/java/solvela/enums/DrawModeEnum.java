package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 抽奖算法，对齐 {@code t_prize_pool_config.draw_mode}。
 *
 * <p>决定一次抽奖怎么在奖池的各个奖品之间做选择：
 * <ul>
 *   <li>{@link #PROBABILITY} 按各奖品配置的中奖概率抽，与剩余库存无关；</li>
 *   <li>{@link #STOCK_RATIO} 按剩余库存的比例抽，库存多的更容易被抽中，天然把奖品发均匀。</li>
 * </ul>
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum DrawModeEnum implements BaseEnum {

    PROBABILITY(1, "按概率"),

    STOCK_RATIO(2, "按库存比例"),
    ;

    private final Integer value;

    private final String desc;
}
