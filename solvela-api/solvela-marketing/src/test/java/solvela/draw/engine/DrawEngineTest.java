package solvela.draw.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抽奖命中引擎 单元测试（纯内存，无 Spring 上下文）
 *
 * @Author alaric
 * @Date 2026-07-26
 */
class DrawEngineTest {

    private static final String POOL = "POOL_TEST";

    /** 便于把「百分之几」写成 ppm，让用例仍然读得出业务含义 */
    private static int pct(String percent) {
        return Ppm.fromPercent(new BigDecimal(percent));
    }

    /**
     * 标准三奖项池：iPhone 0.05% / 积分 10.95% / 谢谢参与(兜底) 89%。
     * 纯配置，不含库存 —— 库存由 {@link #stock} 单独给。
     */
    private static List<DrawSlot> standardSlots() {
        return List.of(
                new DrawSlot(new DrawPrizeSnapshot(1L, "PRIZE_IPHONE", false, Set.of("vip_10086")), pct("0.05")),
                new DrawSlot(new DrawPrizeSnapshot(2L, "PRIZE_SCORE", false, Set.of()), pct("10.95")),
                new DrawSlot(new DrawPrizeSnapshot(3L, "PRIZE_THANKS", true, Set.of()), pct("89")));
    }

    private static DrawPoolSnapshot standardPool() {
        return DrawPoolSnapshot.of(POOL, standardSlots());
    }

    /** 标准池的库存：兜底恒为不限量 */
    private static LocalInventory stock(int iphone, int score) {
        return LocalInventory.of(standardSlots(), new int[]{iphone, score, LocalInventory.UNLIMITED});
    }

    private static DrawSlot slot(long id, String code, boolean fallback, String percent) {
        return new DrawSlot(new DrawPrizeSnapshot(id, code, fallback, Set.of()), pct(percent));
    }

    // ==================== 概率单位 ====================

    @Test
    @DisplayName("百分比 -> ppm：0.05% = 500，100% = 1000000")
    void percentToPpm() {
        assertEquals(500, pct("0.05"));
        assertEquals(109_500, pct("10.95"));
        assertEquals(Ppm.FULL, pct("100"));
        assertEquals("10.9500%", Ppm.toPercentText(109_500));
    }

    @Test
    @DisplayName("小数位超过 4 位直接抛，不静默舍入")
    void percentBeyondColumnPrecisionRejected() {
        assertThrows(ArithmeticException.class, () -> Ppm.fromPercent(new BigDecimal("0.00005")),
                "列是 decimal(8,4)，第 5 位小数存不下。静默舍入的话某个坑位的概率会悄悄变化，"
                        + "而闭环校验依然通过 —— 那是最难查的一类配置事故。");
    }

    // ==================== 概率区间 ====================

    @Test
    @DisplayName("概率未闭环的快照直接拒绝构造")
    void poolMustBeClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> DrawPoolSnapshot.of(POOL, List.of(slot(1L, "A", false, "99"))));
    }

    @Test
    @DisplayName("🔴 99.9999% 不再算闭环 —— 整数化换来的精确校验")
    void closureIsExactNotFuzzy() {
        assertThrows(IllegalArgumentException.class,
                () -> DrawPoolSnapshot.of(POOL, List.of(
                        slot(1L, "A", false, "10"),
                        slot(2L, "FB", true, "89.9999"))),
                "改造前闭环是 |total - 100| <= 0.0001 的容差判定，99.9999% 会被判为「闭环」，"
                        + "于是随机数真的可能落在所有区间之外，靠 locate 静默回落到最后一个区间兜住 —— "
                        + "配置错了，但表现只是概率悄悄偏斜，没有任何报错。"
                        + "整数下 total 必须恰好等于 1000000，这类配置在构造快照时就被拒。");
    }

    @Test
    @DisplayName("区间边界：0 命中第一项，999999 命中兜底")
    void boundary() {
        DrawPoolSnapshot pool = standardPool();
        LocalInventory inventory = stock(5, 100);

        DrawResult atZero = DrawEngine.draw(pool, "nobody", inventory, 0);
        assertEquals("PRIZE_IPHONE", ((DrawResult.Hit) atZero).prize().prizeCode());

        DrawResult nearTop = DrawEngine.draw(pool, "nobody", inventory, Ppm.FULL - 1);
        assertEquals("PRIZE_THANKS", ((DrawResult.Hit) nearTop).prize().prizeCode());
    }

    @Test
    @DisplayName("随机数等于 FULL 时抛异常，而不是静默回落到最后一个区间")
    void randomOutOfRangeFailsLoud() {
        DrawPoolSnapshot pool = standardPool();
        LocalInventory inventory = stock(5, 100);
        assertThrows(IllegalStateException.class, () -> DrawEngine.draw(pool, "nobody", inventory, Ppm.FULL),
                "区间铺满 [0, FULL) 且 nextInt(FULL) 取不到 FULL，所以这条路生产上不可达。"
                        + "真走到了只可能是有人绕过 DrawPoolSnapshot.of 构造了非法快照 —— 该炸。");
    }

    @Test
    @DisplayName("10万次固定种子模拟：实际分布贴合理论概率（±0.5个百分点）")
    void distribution() {
        DrawPoolSnapshot pool = standardPool();
        LocalInventory inventory = stock(LocalInventory.UNLIMITED, LocalInventory.UNLIMITED);
        Random random = new Random(20260726L);
        int times = 100_000;
        Map<String, Integer> counter = new HashMap<>();
        for (int i = 0; i < times; i++) {
            DrawResult result = DrawEngine.draw(pool, "nobody", inventory, random.nextInt(Ppm.FULL));
            String code = ((DrawResult.Hit) result).prize().prizeCode();
            counter.merge(code, 1, Integer::sum);
        }
        double iphoneRate = counter.getOrDefault("PRIZE_IPHONE", 0) * 100.0 / times;
        double scoreRate = counter.getOrDefault("PRIZE_SCORE", 0) * 100.0 / times;
        double thanksRate = counter.getOrDefault("PRIZE_THANKS", 0) * 100.0 / times;
        assertTrue(Math.abs(iphoneRate - 0.05) < 0.5, "iPhone 实际=" + iphoneRate);
        assertTrue(Math.abs(scoreRate - 10.95) < 0.5, "积分 实际=" + scoreRate);
        assertTrue(Math.abs(thanksRate - 89) < 0.5, "兜底 实际=" + thanksRate);
    }

    // ==================== 白名单 ====================

    @Test
    @DisplayName("白名单必中：即使随机数落在兜底区间，名单内用户仍命中白名单奖项")
    void whiteListWins() {
        DrawPoolSnapshot pool = standardPool();
        LocalInventory inventory = stock(5, 100);
        // 随机数 50% 落在兜底区间，但 vip_10086 在 iPhone 白名单内
        DrawResult result = DrawEngine.draw(pool, "vip_10086", inventory, pct("50"));
        DrawResult.Hit hit = assertInstanceOf(DrawResult.Hit.class, result);
        assertEquals("PRIZE_IPHONE", hit.prize().prizeCode());
        assertEquals(DrawResult.HitSource.WHITE_LIST, hit.source());
    }

    @Test
    @DisplayName("白名单仍受库存约束：目标奖项无库存时回落到概率命中")
    void whiteListRespectsStock() {
        DrawPoolSnapshot pool = standardPool();
        LocalInventory inventory = stock(0, 100);
        DrawResult result = DrawEngine.draw(pool, "vip_10086", inventory, pct("50"));
        DrawResult.Hit hit = assertInstanceOf(DrawResult.Hit.class, result);
        assertEquals("PRIZE_THANKS", hit.prize().prizeCode());
        assertEquals(DrawResult.HitSource.PROBABILITY, hit.source());
    }

    // ==================== 库存降级 ====================

    @Test
    @DisplayName("命中奖项无库存降级到兜底，来源标记为 FALLBACK_DEGRADE")
    void degradeToFallback() {
        DrawPoolSnapshot pool = standardPool();
        LocalInventory inventory = stock(5, 0);
        // 随机数 5% 落在积分区间 [0.05%, 11%)，但积分库存为 0
        DrawResult result = DrawEngine.draw(pool, "nobody", inventory, pct("5"));
        DrawResult.Hit hit = assertInstanceOf(DrawResult.Hit.class, result);
        assertEquals("PRIZE_THANKS", hit.prize().prizeCode());
        assertEquals(DrawResult.HitSource.FALLBACK_DEGRADE, hit.source());
    }

    @Test
    @DisplayName("命中奖项与兜底同时无库存 -> NoStock，携带候选奖品编码")
    void noStockWhenFallbackDry() {
        List<DrawSlot> slots = List.of(slot(1L, "PRIZE_A", false, "40"), slot(2L, "PRIZE_FB", true, "60"));
        DrawPoolSnapshot pool = DrawPoolSnapshot.of(POOL, slots);
        LocalInventory inventory = LocalInventory.of(slots, new int[]{0, 0});
        DrawResult result = DrawEngine.draw(pool, "nobody", inventory, pct("10"));
        DrawResult.NoStock noStock = assertInstanceOf(DrawResult.NoStock.class, result);
        assertEquals("PRIZE_A", noStock.candidatePrizeCode());
    }

    @Test
    @DisplayName("兜底自己被抽中且无库存时不自我降级，直接 NoStock")
    void fallbackHitButDryNoSelfDegrade() {
        List<DrawSlot> slots = List.of(slot(1L, "PRIZE_A", false, "40"), slot(2L, "PRIZE_FB", true, "60"));
        DrawPoolSnapshot pool = DrawPoolSnapshot.of(POOL, slots);
        LocalInventory inventory = LocalInventory.of(slots, new int[]{10, 0});
        // 随机数落在兜底区间，兜底无库存，且不能降级到自己
        DrawResult result = DrawEngine.draw(pool, "nobody", inventory, pct("70"));
        assertInstanceOf(DrawResult.NoStock.class, result);
    }

    // ==================== sealed 模式匹配消费示例 ====================

    @Test
    @DisplayName("switch 模式匹配解构消费 DrawResult（穷尽性由编译器保证）")
    void patternMatchingConsumption() {
        DrawPoolSnapshot pool = standardPool();
        LocalInventory inventory = stock(5, 100);
        DrawResult result = DrawEngine.draw(pool, "vip_10086", inventory, pct("50"));

        String display = switch (result) {
            case DrawResult.Hit(DrawPrizeSnapshot prize, DrawResult.HitSource source) ->
                    "恭喜中奖:" + prize.prizeCode() + "(来源:" + source + ")";
            case DrawResult.NoStock(String candidate) ->
                    "手慢了，" + candidate + " 已被抽完";
        };
        assertEquals("恭喜中奖:PRIZE_IPHONE(来源:WHITE_LIST)", display);
    }
}
