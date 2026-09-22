package solvela.mall.clientapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.mall.MallCommodity;
import solvela.mall.MallSku;
import solvela.mall.commodity.MallPricing;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商城定价：<b>页面显示的价，必须就是下单扣的分</b>。
 *
 * <h3>🔴 这条测试对应一个真实的显示错误</h3>
 * 2026-09-22：{@code MallClientFacade} 把 nullable 的 SKU 价<b>原样</b>发给端上，
 * 而它自己的注释写着「端上拿到的一定是算好的值」—— 注释对，代码没跟上。
 *
 * <p>于是「SKU 没填价就继承商品基准价」这件事跑到了端上，两个页面只有一个记得做：
 * {@code ProductView.vue} 写了 {@code ?? 商品基准价}（蒙对了），
 * {@code RedeemView.vue} 写的是 {@code ?? 0} —— <b>兑换页显示 0 分，服务端照基准价扣</b>。
 * 库里当时就有这样一条 SKU（「HUAWEI WATCH GT 7 Pro（46mm）」，基准价 19990），
 * 只是它库存为 0 才没被点到。
 *
 * <h3>⚠️ 所以这里守的是两件事</h3>
 * ① 继承规则本身算得对；
 * ② <b>两条路都调同一份规则</b> —— 展示和扣减各写一套，迟早对不上，
 *    而那种不一致不报错、只体现为「价格不对」。
 *
 * @Date 2026-09-22
 */
class MallPricingTest {

    private static MallSku sku(Integer points, BigDecimal cash) {
        MallSku s = new MallSku();
        s.setSkuPointsPrice(points);
        s.setSkuCashPrice(cash);
        return s;
    }

    private static MallCommodity commodity(Integer points, BigDecimal cash) {
        MallCommodity c = new MallCommodity();
        c.setPointsPrice(points);
        c.setCashPrice(cash);
        return c;
    }

    @Test
    @DisplayName("SKU 填了价就用 SKU 的（不同规格可以不同价）")
    void sku价优先() {
        assertEquals(8800, MallPricing.points(sku(8800, null), commodity(19990, null)));
    }

    @Test
    @DisplayName("🔴 SKU 没填价继承商品基准价 —— 不是 0")
    void sku价为空时继承基准价() {
        // 这正是线上那条：SKU 价 NULL，商品基准价 19990。端上曾经把它显示成 0
        assertEquals(19990, MallPricing.points(sku(null, null), commodity(19990, null)));
    }

    @Test
    @DisplayName("🔴 SKU 价是 0 要当真免费，不能当成「没填」")
    void 零是合法价不是未设置() {
        /*
         * DDL 刻意允许 NULL 而非默认 0。判空写成 `<= 0` 的话，
         * 一件真免费的商品会被悄悄按商品基准价收费 —— 而那是用户白付钱的方向。
         */
        assertEquals(0, MallPricing.points(sku(0, null), commodity(19990, null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                MallPricing.cash(sku(null, BigDecimal.ZERO), commodity(null, new BigDecimal("99")))));
    }

    @Test
    @DisplayName("两边都没填：积分 0、现金 0，不抛空指针")
    void 都没填时兜底为零() {
        assertEquals(0, MallPricing.points(sku(null, null), commodity(null, null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(MallPricing.cash(sku(null, null), commodity(null, null))));
    }

    @Test
    @DisplayName("现金价规则与积分价一致")
    void 现金价同规则() {
        assertEquals(0, new BigDecimal("12.50").compareTo(
                MallPricing.cash(sku(null, new BigDecimal("12.50")), commodity(null, new BigDecimal("99")))));
        assertEquals(0, new BigDecimal("99").compareTo(
                MallPricing.cash(sku(null, null), commodity(null, new BigDecimal("99")))));
    }

    /**
     * 🔴 守「两条路共用一份规则」。
     *
     * <p>断言的是源码里<b>没有第二份实现</b>：展示侧和下单侧都必须经过 MallPricing。
     * 有人为了图快在某一侧重新写一遍 {@code getSkuPointsPrice() != null ? ... : ...}，
     * 上面那几条用例一条都不会红 —— 因为它们测的是 MallPricing，而那个人没走它。
     */
    @Test
    @DisplayName("🔴 展示侧与下单侧都必须走 MallPricing，不许各写一份")
    void 没有第二份实现() throws IOException {
        for (String path : new String[]{
                "src/main/java/solvela/mall/clientapi/MallClientFacade.java",
                "src/main/java/solvela/mall/order/service/MallRedeemService.java"}) {
            String src = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            assertTrue(src.contains("MallPricing."), path + " 没有调用 MallPricing");
            // 自己读 SKU 价再三元兜底 = 又分叉了一次
            assertFalse(src.contains("getSkuPointsPrice() != null"),
                    path + " 里又写了一份继承逻辑，请改调 MallPricing");
        }
    }
}
