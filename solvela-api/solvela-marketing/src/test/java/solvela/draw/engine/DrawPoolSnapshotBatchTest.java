package solvela.draw.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 连抽的库存递减。<b>这是连抽唯一新增的、会算错的地方。</b>
 *
 * <h3>不做这件事会怎样</h3>
 * 快照里的 {@code remainStock} 是抽之前的值。10 连抽若全部拿同一份快照判定：
 * 一个只剩 1 份的奖会被判定 10 次「有货」，而实际只有第一次扣得动，
 * 后 9 次全部走 fallback —— 用户看到的是「9 连保底」，而且不会有任何报错。
 *
 * <p>所以 {@code DrawExecuteService} 每抽中一次就 {@code withStockConsumed} 一次，
 * 拿更新后的快照进下一轮。本类钉住那个方法的行为。
 */
class DrawPoolSnapshotBatchTest {

    private static final String POOL = "POOL_BATCH";

    /** 一个奖项概率 100%，方便让判定必然落在它身上 */
    private static DrawPoolSnapshot singlePrizePool(int stock) {
        return DrawPoolSnapshot.of(POOL, List.of(new DrawSlot(
                new DrawPrizeSnapshot(1L, "PRIZE_ONLY", false, stock, Set.of()), Ppm.FULL)));
    }

    @Test
    @DisplayName("消耗一份库存后，快照里那个奖项少 1")
    void 消耗后库存减一() {
        DrawPoolSnapshot before = singlePrizePool(3);
        DrawPoolSnapshot after = before.withStockConsumed(1L);

        assertEquals(3, before.ranges().getFirst().prize().remainStock(),
                "原快照必须没被改动 —— 它是不可变的纯函数输入");
        assertEquals(2, after.ranges().getFirst().prize().remainStock());
        assertNotSame(before, after);
    }

    @Test
    @DisplayName("🔴 只剩 1 份的奖，第二次判定就不该再有货")
    void 连抽不会把同一份库存判定两次() {
        DrawPoolSnapshot snapshot = singlePrizePool(1);

        assertInstanceOf(DrawResult.Hit.class, DrawEngine.draw(snapshot, "someone"),
                "第一次该中：库存还有 1");

        snapshot = snapshot.withStockConsumed(1L);

        assertInstanceOf(DrawResult.NoStock.class, DrawEngine.draw(snapshot, "someone"),
                """
                        第二次仍然判定为「有货」—— 说明连抽时没有逐次更新快照。
                        后果不是报错，是所有后续抽奖都会走 fallback 降级：
                        用户抽 10 次，第 1 次中了真奖，后 9 次全是保底，看起来像被针对了。
                        """);
    }

    @Test
    @DisplayName("不限量的奖项不会被减成负数")
    void 不限量奖项不受影响() {
        DrawPoolSnapshot snapshot = DrawPoolSnapshot.of(POOL, List.of(new DrawSlot(
                new DrawPrizeSnapshot(9L, "PRIZE_UNLIMITED", true, DrawPrizeSnapshot.UNLIMITED, Set.of()),
                Ppm.FULL)));

        DrawPoolSnapshot after = snapshot.withStockConsumed(9L).withStockConsumed(9L);

        assertEquals(DrawPrizeSnapshot.UNLIMITED, after.ranges().getFirst().prize().remainStock(),
                "UNLIMITED 是 -1，减下去就变成 -2 -3，hasStock() 会当成无库存 —— 兜底奖项从此永远发不出");
    }

    @Test
    @DisplayName("库存已经是 0 时再消耗，不会减成负数")
    void 零库存不会变负() {
        DrawPoolSnapshot after = singlePrizePool(0).withStockConsumed(1L);
        assertEquals(0, after.ranges().getFirst().prize().remainStock());
    }

    @Test
    @DisplayName("消耗一个不属于本池的奖项 id，快照原样返回")
    void 未知奖项静默忽略() {
        DrawPoolSnapshot before = singlePrizePool(3);
        DrawPoolSnapshot after = before.withStockConsumed(999L);

        assertEquals(3, after.ranges().getFirst().prize().remainStock(),
                "库存的最终裁决权在 Redis，这里为一个用不上的 id 抛异常只会让热路径更脆");
    }

    @Test
    @DisplayName("概率区间不受影响 —— 否则构造器的闭环校验会炸")
    void 消耗库存不动概率区间() {
        DrawPoolSnapshot before = DrawPoolSnapshot.of(POOL, List.of(
                new DrawSlot(new DrawPrizeSnapshot(1L, "A", false, 2, Set.of()), 300_000),
                new DrawSlot(new DrawPrizeSnapshot(2L, "B", true, DrawPrizeSnapshot.UNLIMITED, Set.of()), 700_000)));

        DrawPoolSnapshot after = before.withStockConsumed(1L);

        List<Integer> beforeMax = new ArrayList<>();
        before.ranges().forEach(r -> beforeMax.add(r.max()));
        List<Integer> afterMax = new ArrayList<>();
        after.ranges().forEach(r -> afterMax.add(r.max()));

        assertEquals(beforeMax, afterMax, "区间变了的话，概率就跟着库存漂移了");
        assertSame(before.fallbackPrize().prizeCode(), after.fallbackPrize().prizeCode());
    }
}
