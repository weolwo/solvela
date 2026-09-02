package solvela.draw.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连抽的批内库存递减。<b>这是连抽唯一新增的、会算错的地方。</b>
 *
 * <h3>不做这件事会怎样</h3>
 * {@link LocalInventory} 里是开抽那一刻的读数。10 连抽若全部拿同一份读数判定：
 * 一个只剩 1 份的奖会被判定 10 次「有货」，而实际只有第一次扣得动，
 * 后 9 次全部走 fallback —— 用户看到的是「9 连保底」，而且不会有任何报错。
 *
 * <p>所以 {@code DrawExecuteService} 每抽中一次就 {@code consume} 一次。本类钉住那个行为。
 *
 * <h3>少了一个用例，是好事</h3>
 * 上一版这里还有一条「消耗库存不动概率区间」—— 因为当时扣库存是
 * {@code DrawPoolSnapshot.withStockConsumed} 逐个重建 {@code ProbabilityRange}，
 * 重建时算错区间就会让概率跟着库存漂移，只能靠用例去钉。
 * 现在库存与概率根本不在一个对象里，那种错误<b>写不出来</b>，用例也就没有存在的必要了。
 */
class LocalInventoryTest {

    private static final long ONLY = 1L;

    private static DrawSlot slot(long id, boolean fallback) {
        return new DrawSlot(new DrawPrizeSnapshot(id, "PRIZE_" + id, fallback, Set.of()), Ppm.FULL);
    }

    /** 单奖项池，概率 100%，方便让判定必然落在它身上 */
    private static DrawPoolSnapshot singlePrizePool() {
        return DrawPoolSnapshot.of("POOL_BATCH", List.of(slot(ONLY, false)));
    }

    private static LocalInventory inventoryOf(int stock) {
        return LocalInventory.of(List.of(slot(ONLY, false)), new int[]{stock});
    }

    @Test
    @DisplayName("消耗一份后就少一份")
    void 消耗后库存减一() {
        LocalInventory inventory = inventoryOf(2);

        assertTrue(inventory.hasStock(ONLY));
        inventory.consume(ONLY);
        assertTrue(inventory.hasStock(ONLY), "还剩 1 份");
        inventory.consume(ONLY);
        assertFalse(inventory.hasStock(ONLY), "扣完了");
    }

    @Test
    @DisplayName("🔴 只剩 1 份的奖，第二次判定就不该再有货")
    void 连抽不会把同一份库存判定两次() {
        DrawPoolSnapshot pool = singlePrizePool();
        LocalInventory inventory = inventoryOf(1);

        assertInstanceOf(DrawResult.Hit.class, DrawEngine.draw(pool, "someone", inventory),
                "第一次该中：库存还有 1");

        inventory.consume(ONLY);

        assertInstanceOf(DrawResult.NoStock.class, DrawEngine.draw(pool, "someone", inventory),
                """
                        第二次仍然判定为「有货」—— 说明连抽时没有逐次更新库存视图。
                        后果不是报错，是所有后续抽奖都会走 fallback 降级：
                        用户抽 10 次，第 1 次中了真奖，后 9 次全是保底，看起来像被针对了。
                        """);
    }

    @Test
    @DisplayName("不限量的奖项不会被减成负数")
    void 不限量奖项不受影响() {
        LocalInventory inventory = inventoryOf(LocalInventory.UNLIMITED);

        inventory.consume(ONLY);
        inventory.consume(ONLY);

        assertTrue(inventory.hasStock(ONLY),
                "UNLIMITED 是 -1，减下去就变成 -2 -3，hasStock 会当成无库存 —— 兜底奖项从此永远发不出");
    }

    @Test
    @DisplayName("库存已经是 0 时再消耗，不会减成负数")
    void 零库存不会变负() {
        LocalInventory inventory = inventoryOf(0);

        inventory.consume(ONLY);
        inventory.consume(ONLY);

        assertFalse(inventory.hasStock(ONLY));
    }

    @Test
    @DisplayName("🔴 问一个不属于本池的奖项，当场抛而不是静默返回无库存")
    void 未知奖项直接抛() {
        LocalInventory inventory = inventoryOf(3);

        assertThrows(IllegalArgumentException.class, () -> inventory.hasStock(999L),
                """
                        视图是用同一份坑位列表建的，引擎与结算问的只会是本池的奖项。
                        走到这里说明奖项与奖池对不上了 —— 静默返回「无库存」会让整池奖品全部发不出，
                        静默忽略扣减则会超发，两种都比当场抛出更难查。
                        """);
        assertThrows(IllegalArgumentException.class, () -> inventory.consume(999L));
    }

    @Test
    @DisplayName("坑位数与库存数对不上时拒绝构造")
    void 坑位与库存必须等长() {
        List<DrawSlot> slots = List.of(slot(ONLY, false));
        assertThrows(IllegalArgumentException.class, () -> LocalInventory.of(slots, new int[]{1, 2}),
                "错位的话每个奖项拿到的是别人的库存，而且不会报错");
    }

    @Test
    @DisplayName("传进来的数组之后再改，动不到视图里的库存")
    void 构造时拷贝入参数组() {
        int[] stocks = {5};
        LocalInventory inventory = LocalInventory.of(List.of(slot(ONLY, false)), stocks);

        stocks[0] = 0;

        assertTrue(inventory.hasStock(ONLY), "调用方那个数组是循环里填出来的，不该继续能改到视图");
        assertEquals(0, stocks[0]);
    }
}
