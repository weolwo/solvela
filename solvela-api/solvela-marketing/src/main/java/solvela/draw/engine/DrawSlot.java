package solvela.draw.engine;

import java.math.BigDecimal;

/**
 * 奖池的一个坑位：一个奖项 + 它的中奖概率。<b>构建快照的输入</b>。
 *
 * <h3>为什么单独有这个类型</h3>
 * 改造前是 {@code DrawPoolSnapshot.of(poolCode, List<DrawPrizeSnapshot>, List<BigDecimal>)} ——
 * 两个必须一一对应的并行列表，于是构造器里专门有一句
 * {@code if (prizes.size() != probabilities.size()) throw ...} 来兜这个设计。
 * 把「奖项」和「它的概率」捆成一个值之后，那句校验直接删掉了：<b>不可能再不等长</b>。
 * 更要紧的是错位 —— 并行列表写错顺序时长度仍然相等，校验拦不住，
 * 表现是每个奖项拿到了别人的概率，而没有任何报错。
 *
 * @param prize 奖项快照
 * @param ppm   中奖概率，单位见 {@link Ppm}。同一奖池所有坑位之和必须恰好 {@link Ppm#FULL}
 */
public record DrawSlot(DrawPrizeSnapshot prize, int ppm) {

    public DrawSlot {
        if (ppm < 0) {
            // 负概率会把后续坑位的命中区间整体往回推，最终闭环校验反而可能通过
            throw new IllegalArgumentException(
                    "概率不能为负: " + prize.prizeCode() + " = " + Ppm.toPercentText(ppm));
        }
        if (ppm > Ppm.FULL) {
            throw new IllegalArgumentException(
                    "单个奖项概率超过 100%: " + prize.prizeCode() + " = " + Ppm.toPercentText(ppm));
        }
    }

    /**
     * 从后台配置的<b>百分比</b>构建坑位 —— 百分比世界与 ppm 世界的唯一入口。
     *
     * @param percent {@code t_pool_prize_mapping.probability}，如 {@code 10.95} 表示 10.95%
     */
    public static DrawSlot ofPercent(DrawPrizeSnapshot prize, BigDecimal percent) {
        return new DrawSlot(prize, Ppm.fromPercent(percent));
    }
}
