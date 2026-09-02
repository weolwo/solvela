package solvela.draw.engine;

import java.util.List;

/**
 * 一批抽奖的<b>批内库存视图</b>：可变，只活在一次 {@code execute} 调用里。
 *
 * <h3>🔴 名字里的 Local 是重点：这不是库存的真相</h3>
 * 库存的裁决权在 Redis Lua 原子预扣 + DB 条件更新，这里存的只是<b>开抽那一刻的读数</b>，
 * 外加本批已经扣掉的部分。它的唯一用途是让引擎在批内不要把同一份库存判定两次。
 * 判定说「有货」而真扣失败是完全正常的（并发抢空、超单人限领），
 * {@code DrawExecuteService.settle} 会降级到兜底。
 *
 * <h3>为什么库存从 DrawPrizeSnapshot 里搬了出来</h3>
 * 上一版库存是奖项快照的一个字段，于是「批内扣掉一份」只能靠
 * {@code DrawPoolSnapshot.withStockConsumed} <b>重建整棵结构</b> ——
 * 为了改一个 int，10 连抽要新建 10 个快照、10 个区间列表、80 个区间对象。
 *
 * <p>拆开之后：{@link DrawPoolSnapshot} 是<b>纯配置</b>（概率、奖项身份、白名单），
 * 整批只读且天然可复用；变的部分只剩这里两个数组，整批只 new 一次，扣减是
 * {@code remain[i]--}，零分配。
 *
 * <p>顺带消掉一个原来只能靠测试守住的约束：<b>扣库存不可能碰到概率区间</b>了 ——
 * 上一版 {@code withStockConsumed} 是逐个重建 {@code ProbabilityRange}，
 * 重建时把区间算错就会让概率跟着库存漂移，所以专门写了用例去钉它。
 * 现在库存与区间根本不在一个对象里，那种错误<b>写不出来</b>。
 *
 * <h3>为什么是数组而不是 Map</h3>
 * 奖池坑位是个位数到十几个。{@code HashMap<Long, Integer>} 的每次查询都要把
 * {@code long} 装箱（奖项 id 远超 Integer 缓存范围，必然分配），而抽奖是热路径。
 * 十几个元素的 {@code long[]} 线性扫描既不分配也更快。
 */
public final class LocalInventory {

    /** 不限量。与 {@code t_prize_pool_item} 的约定一致 */
    public static final int UNLIMITED = -1;

    private final long[] prizeItemIds;
    private final int[] remain;

    private LocalInventory(long[] prizeItemIds, int[] remain) {
        this.prizeItemIds = prizeItemIds;
        this.remain = remain;
    }

    /**
     * 按坑位顺序建立库存视图。
     *
     * @param slots        奖池坑位，顺序与 {@code remainStocks} 一一对应
     * @param remainStocks 各坑位开抽那一刻的剩余库存（{@link #UNLIMITED} 表示不限量）
     */
    public static LocalInventory of(List<DrawSlot> slots, int[] remainStocks) {
        if (slots.size() != remainStocks.length) {
            throw new IllegalArgumentException(
                    "坑位与库存数量不一致: slots=" + slots.size() + ", stocks=" + remainStocks.length);
        }
        long[] ids = new long[slots.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = slots.get(i).prize().prizeItemId();
        }
        // 防御性拷贝：调用方那个数组是循环里填出来的，别让它继续能改到这里
        return new LocalInventory(ids, remainStocks.clone());
    }

    /** 批内视角下这个奖项还发不发得出来 */
    public boolean hasStock(long prizeItemId) {
        int stock = remain[indexOf(prizeItemId)];
        return stock == UNLIMITED || stock > 0;
    }

    /**
     * 扣掉一份（不限量的奖项不动，已经是 0 的也不会减成负数）。
     *
     * <p>只在<b>真的扣成功之后</b>调用 —— 引擎的判定是预测，
     * 按预测扣会让这个视图与真实结果漂移。
     */
    public void consume(long prizeItemId) {
        int index = indexOf(prizeItemId);
        if (remain[index] != UNLIMITED && remain[index] > 0) {
            remain[index]--;
        }
    }

    /**
     * 找不到就抛，<b>不静默忽略</b>。
     *
     * <p>本视图是用同一份坑位列表建的，引擎与结算问的也只会是这个池里的奖项 ——
     * 走到这里说明奖项与奖池对不上了，那是代码错误。静默返回「无库存」会让整池
     * 奖品全部发不出，静默忽略扣减则会超发，两种都比当场抛出更难查。
     */
    private int indexOf(long prizeItemId) {
        for (int i = 0; i < prizeItemIds.length; i++) {
            if (prizeItemIds[i] == prizeItemId) {
                return i;
            }
        }
        throw new IllegalArgumentException("奖项不属于本奖池: prizeItemId=" + prizeItemId);
    }
}
