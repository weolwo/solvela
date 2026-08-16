package sa.draw.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * 标准三奖项池：iPhone 0.05% / 积分 10.95% / 谢谢参与(兜底) 89%
     */
    private DrawPoolSnapshot standardPool(int iphoneStock, int scoreStock) {
        List<DrawPrizeSnapshot> prizes = List.of(
                new DrawPrizeSnapshot(1L, "PRIZE_IPHONE", false, iphoneStock, Set.of("vip_10086")),
                new DrawPrizeSnapshot(2L, "PRIZE_SCORE", false, scoreStock, Set.of()),
                new DrawPrizeSnapshot(3L, "PRIZE_THANKS", true, DrawPrizeSnapshot.UNLIMITED, Set.of())
        );
        List<BigDecimal> probs = List.of(new BigDecimal("0.05"), new BigDecimal("10.95"), new BigDecimal("89"));
        return DrawPoolSnapshot.of(POOL, prizes, probs);
    }

    // ==================== 概率区间 ====================

    @Test
    @DisplayName("概率未闭环的快照直接拒绝构造")
    void poolMustBeClosed() {
        List<DrawPrizeSnapshot> prizes = List.of(new DrawPrizeSnapshot(1L, "A", false, 1, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DrawPoolSnapshot.of(POOL, prizes, List.of(new BigDecimal("99"))));
    }

    @Test
    @DisplayName("区间边界：0 命中第一项，99.9999 命中兜底，恰好越界回落最后区间")
    void boundary() {
        DrawPoolSnapshot pool = standardPool(5, 100);

        DrawResult atZero = DrawEngine.draw(pool, "nobody", new BigDecimal("0"));
        assertEquals("PRIZE_IPHONE", ((DrawResult.Hit) atZero).prize().prizeCode());

        DrawResult nearTop = DrawEngine.draw(pool, "nobody", new BigDecimal("99.9999"));
        assertEquals("PRIZE_THANKS", ((DrawResult.Hit) nearTop).prize().prizeCode());

        // 理论不可达的 100（左闭右开落不进任何区间），防御性回落到最后一个区间而不是抛异常
        DrawResult atTop = DrawEngine.draw(pool, "nobody", new BigDecimal("100"));
        assertEquals("PRIZE_THANKS", ((DrawResult.Hit) atTop).prize().prizeCode());
    }

    @Test
    @DisplayName("10万次固定种子模拟：实际分布贴合理论概率（±0.5个百分点）")
    void distribution() {
        DrawPoolSnapshot pool = standardPool(DrawPrizeSnapshot.UNLIMITED, DrawPrizeSnapshot.UNLIMITED);
        Random random = new Random(20260726L);
        int times = 100_000;
        Map<String, Integer> counter = new HashMap<>();
        for (int i = 0; i < times; i++) {
            BigDecimal rand = BigDecimal.valueOf(random.nextDouble()).multiply(new BigDecimal("100"))
                    .setScale(4, RoundingMode.DOWN);
            DrawResult result = DrawEngine.draw(pool, "nobody", rand);
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
        DrawPoolSnapshot pool = standardPool(5, 100);
        // 随机数 50 落在兜底区间，但 vip_10086 在 iPhone 白名单内
        DrawResult result = DrawEngine.draw(pool, "vip_10086", new BigDecimal("50"));
        DrawResult.Hit hit = assertInstanceOf(DrawResult.Hit.class, result);
        assertEquals("PRIZE_IPHONE", hit.prize().prizeCode());
        assertEquals(DrawResult.HitSource.WHITE_LIST, hit.source());
    }

    @Test
    @DisplayName("白名单仍受库存约束：目标奖项无库存时回落到概率命中")
    void whiteListRespectsStock() {
        DrawPoolSnapshot pool = standardPool(0, 100);
        DrawResult result = DrawEngine.draw(pool, "vip_10086", new BigDecimal("50"));
        DrawResult.Hit hit = assertInstanceOf(DrawResult.Hit.class, result);
        assertEquals("PRIZE_THANKS", hit.prize().prizeCode());
        assertEquals(DrawResult.HitSource.PROBABILITY, hit.source());
    }

    // ==================== 库存降级 ====================

    @Test
    @DisplayName("命中奖项无库存降级到兜底，来源标记为 FALLBACK_DEGRADE")
    void degradeToFallback() {
        DrawPoolSnapshot pool = standardPool(5, 0);
        // 随机数 5 落在积分区间 [0.05, 11)，但积分库存为 0
        DrawResult result = DrawEngine.draw(pool, "nobody", new BigDecimal("5"));
        DrawResult.Hit hit = assertInstanceOf(DrawResult.Hit.class, result);
        assertEquals("PRIZE_THANKS", hit.prize().prizeCode());
        assertEquals(DrawResult.HitSource.FALLBACK_DEGRADE, hit.source());
    }

    @Test
    @DisplayName("命中奖项与兜底同时无库存 -> NoStock，携带候选奖品编码")
    void noStockWhenFallbackDry() {
        List<DrawPrizeSnapshot> prizes = List.of(
                new DrawPrizeSnapshot(1L, "PRIZE_A", false, 0, Set.of()),
                new DrawPrizeSnapshot(2L, "PRIZE_FB", true, 0, Set.of())
        );
        DrawPoolSnapshot pool = DrawPoolSnapshot.of(POOL, prizes,
                List.of(new BigDecimal("40"), new BigDecimal("60")));
        DrawResult result = DrawEngine.draw(pool, "nobody", new BigDecimal("10"));
        DrawResult.NoStock noStock = assertInstanceOf(DrawResult.NoStock.class, result);
        assertEquals("PRIZE_A", noStock.candidatePrizeCode());
    }

    @Test
    @DisplayName("兜底自己被抽中且无库存时不自我降级，直接 NoStock")
    void fallbackHitButDryNoSelfDegrade() {
        List<DrawPrizeSnapshot> prizes = List.of(
                new DrawPrizeSnapshot(1L, "PRIZE_A", false, 10, Set.of()),
                new DrawPrizeSnapshot(2L, "PRIZE_FB", true, 0, Set.of())
        );
        DrawPoolSnapshot pool = DrawPoolSnapshot.of(POOL, prizes,
                List.of(new BigDecimal("40"), new BigDecimal("60")));
        // 随机数落在兜底区间，兜底无库存，且不能降级到自己
        DrawResult result = DrawEngine.draw(pool, "nobody", new BigDecimal("70"));
        assertInstanceOf(DrawResult.NoStock.class, result);
    }

    // ==================== sealed 模式匹配消费示例 ====================

    @Test
    @DisplayName("switch 模式匹配解构消费 DrawResult（穷尽性由编译器保证）")
    void patternMatchingConsumption() {
        DrawPoolSnapshot pool = standardPool(5, 100);
        DrawResult result = DrawEngine.draw(pool, "vip_10086", new BigDecimal("50"));

        String display = switch (result) {
            case DrawResult.Hit(DrawPrizeSnapshot prize, DrawResult.HitSource source) ->
                    "恭喜中奖:" + prize.prizeCode() + "(来源:" + source + ")";
            case DrawResult.NoStock(String candidate) ->
                    "手慢了，" + candidate + " 已被抽完";
        };
        assertEquals("恭喜中奖:PRIZE_IPHONE(来源:WHITE_LIST)", display);
    }
}
