package solvela.draw.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 奖池快照：一次抽奖所需的全部只读配置（概率区间已按坑位顺序累加构建）
 * 概率闭环由配置保存端保证（=100%），此处构造时兜底校验一次
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawPoolSnapshot(String poolCode, List<ProbabilityRange> ranges) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal EPSILON = new BigDecimal("0.0001");

    public DrawPoolSnapshot {
        ranges = List.copyOf(ranges);
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("奖池快照不能为空: " + poolCode);
        }
        BigDecimal total = ranges.get(ranges.size() - 1).max();
        if (total.subtract(HUNDRED).abs().compareTo(EPSILON) > 0) {
            throw new IllegalArgumentException("奖池概率未闭环: " + poolCode + " total=" + total);
        }
    }

    /**
     * 按坑位顺序 + 概率构建区间快照
     *
     * @param poolCode      奖池编码
     * @param prizes        奖项快照（坑位顺序）
     * @param probabilities 对应概率（与 prizes 一一对应）
     */
    public static DrawPoolSnapshot of(String poolCode, List<DrawPrizeSnapshot> prizes, List<BigDecimal> probabilities) {
        if (prizes.size() != probabilities.size()) {
            throw new IllegalArgumentException("奖项与概率数量不一致");
        }
        List<ProbabilityRange> ranges = new ArrayList<>(prizes.size());
        BigDecimal acc = BigDecimal.ZERO;
        for (int i = 0; i < prizes.size(); i++) {
            BigDecimal next = acc.add(probabilities.get(i));
            ranges.add(new ProbabilityRange(prizes.get(i), acc, next));
            acc = next;
        }
        return new DrawPoolSnapshot(poolCode, ranges);
    }

    /**
     * 本池的兜底奖项（无则返回 null）
     */
    /**
     * 返回一份「某个奖项被消耗掉一份库存」的新快照。<b>连抽专用。</b>
     *
     * <h3>为什么连抽必须逐次更新快照</h3>
     * 快照里的 {@code remainStock} 是<b>抽之前</b>的值。10 连抽若全部拿同一份快照判定：
     * 某个奖只剩 1 个时，10 次判定都会认为它「有货」，而实际只有第一次扣得动，
     * 后 9 次全部走 fallback 降级 —— 用户看到的是「9 连保底」。
     *
     * <h3>为什么是「消耗后重建」而不是让引擎自己批量抽</h3>
     * 引擎的判定是<b>预测</b>，真实扣减（Redis 预扣 + DB 兜底）可能失败。
     * 让引擎一次判完 N 次，它的库存视图会与真实结果漂移。
     * 所以循环留在 service 里：<b>抽一次 → 真扣一次 → 按真实结果更新快照 → 再抽</b>，
     * 引擎则保持「一次判定、纯函数」不变。
     *
     * <p>不限量的奖项（{@code UNLIMITED}）原样返回，不会被减成 -2。
     * 找不到该奖项时原样返回自己 —— 那说明调用方拿了个不属于本池的 id，
     * 静默忽略比抛异常好：这个方法在热路径上，而库存的最终裁决权本来就在 Redis。
     *
     * <p>概率区间不变，所以构造器的闭环校验照样通过。
     */
    public DrawPoolSnapshot withStockConsumed(long prizeItemId) {
        List<ProbabilityRange> updated = new ArrayList<>(ranges.size());
        for (ProbabilityRange range : ranges) {
            DrawPrizeSnapshot prize = range.prize();
            if (prize.prizeItemId() == prizeItemId
                    && prize.remainStock() != DrawPrizeSnapshot.UNLIMITED
                    && prize.remainStock() > 0) {
                prize = new DrawPrizeSnapshot(prize.prizeItemId(), prize.prizeCode(), prize.fallback(),
                        prize.remainStock() - 1, prize.whiteList());
            }
            updated.add(new ProbabilityRange(prize, range.min(), range.max()));
        }
        return new DrawPoolSnapshot(poolCode, updated);
    }

    public DrawPrizeSnapshot fallbackPrize() {
        return ranges.stream().map(ProbabilityRange::prize).filter(DrawPrizeSnapshot::fallback).findFirst().orElse(null);
    }
}
