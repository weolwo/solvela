package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单次抽奖的结果，对齐 {@code t_draw_prize_log.status}。
 *
 * <p>⚠️ {@link #MISS} 与 {@link #NO_STOCK} 要分开：前者是<b>按算法就是没中</b>，
 * 后者是<b>中了但奖品没库存</b>。对用户来说都是「没拿到」，对运营完全不是一回事 ——
 * 后者意味着奖池配少了，是要立刻补货的信号。混成一个值就再也分不出来了。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum DrawResultEnum implements BaseEnum {

    MISS(0, "未中奖"),

    HIT(1, "已中奖"),

    /**
     * 命中了奖品但扣库存失败 —— 运营视角的补货信号
     */
    NO_STOCK(2, "库存不足"),

    ABNORMAL(3, "异常"),
    ;

    private final Integer value;

    private final String desc;
}
