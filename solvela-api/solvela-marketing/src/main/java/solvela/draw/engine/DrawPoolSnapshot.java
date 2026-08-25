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
    public DrawPrizeSnapshot fallbackPrize() {
        return ranges.stream().map(ProbabilityRange::prize).filter(DrawPrizeSnapshot::fallback).findFirst().orElse(null);
    }
}
