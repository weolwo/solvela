package solvela.draw.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 奖池快照：一次抽奖所需的全部只读配置（概率区间已按坑位顺序累加构建）。
 *
 * <p>概率闭环由配置保存端保证，此处构造时再兜底校验一次 —— 单位是 {@link Ppm}，
 * 所以这里的校验是<b>精确相等</b>而不是容差比较。
 *
 * <h3>纯配置，不含库存</h3>
 * 整批只读。批内会变的库存在 {@link LocalInventory} 里 —— 上一版库存是奖项快照的字段，
 * 于是「扣掉一份」得靠 {@code withStockConsumed} 重建整棵结构：为了改一个 int，
 * 10 连抽要新建 10 个快照、10 个区间列表、80 个区间对象。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawPoolSnapshot(String poolCode, List<ProbabilityRange> ranges) {

    public DrawPoolSnapshot {
        ranges = List.copyOf(ranges);
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("奖池快照不能为空: " + poolCode);
        }
        int total = ranges.getLast().max();
        if (total != Ppm.FULL) {
            throw new IllegalArgumentException("奖池概率未闭环: " + poolCode
                    + " total=" + Ppm.toPercentText(total));
        }
    }

    /**
     * 按坑位顺序累加构建区间快照。
     *
     * @param poolCode 奖池编码
     * @param slots    坑位（奖项 + 概率），<b>顺序即命中区间顺序</b>
     */
    public static DrawPoolSnapshot of(String poolCode, List<DrawSlot> slots) {
        List<ProbabilityRange> ranges = new ArrayList<>(slots.size());
        int acc = 0;
        for (DrawSlot slot : slots) {
            int next = acc + slot.ppm();
            ranges.add(new ProbabilityRange(slot.prize(), acc, next));
            acc = next;
        }
        return new DrawPoolSnapshot(poolCode, ranges);
    }

    /**
     * 本池的兜底奖项（无则返回 null）。多个兜底时只认坑位顺序里的第一个。
     *
     * <p>用普通循环而不是 stream：它在抽奖热路径上每次最多被调两次，
     * 而一条 {@code stream().map().filter().findFirst()} 要分配四五个中间对象。
     */
    public DrawPrizeSnapshot fallbackPrize() {
        for (ProbabilityRange range : ranges) {
            if (range.prize().fallback()) {
                return range.prize();
            }
        }
        return null;
    }
}
