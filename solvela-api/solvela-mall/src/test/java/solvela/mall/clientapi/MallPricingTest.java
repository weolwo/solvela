package solvela.mall.clientapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.mall.MallCommodity;
import solvela.mall.MallSku;
import solvela.mall.commodity.GradeDiscount;
import solvela.mall.commodity.MallPricing;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
 * <h3>2026-09-23 加了等级价，这里多守一件事</h3>
 * ③ <b>挂牌价与实付价是两个名字</b>（{@code listPoints} / {@code points}）。
 *    加等级价那天老的两参 {@code points(sku, commodity)} 是改名不是保留 ——
 *    保留的话任何一处忘了换成三参版本都会悄悄按原价算，和 ① 里那次一模一样。
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

    /** 不参与等级折扣的商品 */
    private static MallCommodity optedOut(Integer points) {
        MallCommodity c = commodity(points, null);
        c.setGradePriceFlag(0);
        return c;
    }

    /** 9 折，没有覆盖价 */
    private static final GradeDiscount NINETY = new GradeDiscount(3, 90, Map.of());

    /*
     * 🔴 这两个刻意【不叫】commodity / sku 去重载。
     *
     * 重载过一次，红了六条用例：`sku(8800, null)` 会优先匹配 (long, Integer)
     * —— int 拓宽成 long 不用装箱，Java 认为它比装箱成 Integer 更合适 ——
     * 于是 8800 成了 id，价格成了 null，整条用例静悄悄地在测另一件事。
     * 名字带 WithId，这种事编译期就长得不一样。
     */

    /** 带 id 的商品 —— 覆盖价按 commodityId 找，没有 id 的商品永远命不中 */
    private static MallCommodity commodityWithId(long id, Integer points) {
        MallCommodity c = commodity(points, null);
        c.setId(id);
        return c;
    }

    private static MallSku skuWithId(long id, Integer points) {
        MallSku s = sku(points, null);
        s.setId(id);
        return s;
    }

    /** 9 折 + 一张覆盖价表 */
    private static GradeDiscount withOverrides(Map<GradeDiscount.Key, Integer> overrides) {
        return new GradeDiscount(3, 90, overrides);
    }

    @Test
    @DisplayName("SKU 填了价就用 SKU 的（不同规格可以不同价）")
    void sku价优先() {
        assertEquals(8800, MallPricing.listPoints(sku(8800, null), commodity(19990, null)));
    }

    @Test
    @DisplayName("🔴 SKU 没填价继承商品基准价 —— 不是 0")
    void sku价为空时继承基准价() {
        // 这正是线上那条：SKU 价 NULL，商品基准价 19990。端上曾经把它显示成 0
        assertEquals(19990, MallPricing.listPoints(sku(null, null), commodity(19990, null)));
    }

    @Test
    @DisplayName("🔴 SKU 价是 0 要当真免费，不能当成「没填」")
    void 零是合法价不是未设置() {
        /*
         * DDL 刻意允许 NULL 而非默认 0。判空写成 `<= 0` 的话，
         * 一件真免费的商品会被悄悄按商品基准价收费 —— 而那是用户白付钱的方向。
         */
        assertEquals(0, MallPricing.listPoints(sku(0, null), commodity(19990, null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                MallPricing.cash(sku(null, BigDecimal.ZERO), commodity(null, new BigDecimal("99")))));
    }

    @Test
    @DisplayName("两边都没填：积分 0、现金 0，不抛空指针")
    void 都没填时兜底为零() {
        assertEquals(0, MallPricing.listPoints(sku(null, null), commodity(null, null)));
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

    // ---------------------------------------------------------------- 等级价

    @Test
    @DisplayName("等级折扣作用在挂牌价上，挂牌价本身不变")
    void 等级折扣算得对() {
        MallSku sku = sku(10000, null);
        MallCommodity c = commodity(19990, null);
        assertEquals(9000, MallPricing.points(sku, c, NINETY));
        assertEquals(10000, MallPricing.listPoints(sku, c), "挂牌价不受折扣影响，划线位要用它");
    }

    @Test
    @DisplayName("🔴 向下取整 —— 积分是整数，必须选对用户有利的方向")
    void 向下取整() {
        /*
         * 9 折的 10001 分 = 9000.9。取上整是 9001，用户实付比「9 折」多一分，
         * 而他会拿计算器算。取下整永远不会让用户多付。
         */
        assertEquals(9000, MallPricing.points(sku(10001, null), commodity(null, null), NINETY));
        // 88 折的 1 分 = 0.88 → 0。一件 1 分的东西对白金免费，是取整方向的必然结果，不是 bug
        assertEquals(0, MallPricing.points(sku(1, null), commodity(null, null), new GradeDiscount(4, 88, Map.of())));
    }

    @Test
    @DisplayName("🔴 商品退出等级折扣时不打折 —— 判断在 MallPricing 里，不在调用方")
    void 商品可以退出() {
        assertEquals(10000, MallPricing.points(sku(null, null), optedOut(10000), NINETY));
        assertFalse(MallPricing.participates(optedOut(10000)));
        assertTrue(MallPricing.participates(commodity(10000, null)), "没设值按参与，与 DDL 默认值一致");
    }

    @Test
    @DisplayName("没有折扣 / 折扣率 100 / discount 为 null，三种都按原价")
    void 不打折的三种形状() {
        MallSku sku = sku(10000, null);
        MallCommodity c = commodity(null, null);
        assertEquals(10000, MallPricing.points(sku, c, GradeDiscount.NONE));
        assertEquals(10000, MallPricing.points(sku, c, new GradeDiscount(4, 100, Map.of())));
        assertEquals(10000, MallPricing.points(sku, c, null));
    }

    @Test
    @DisplayName("🔴 0 分商品打折还是 0，不会算出负数")
    void 零分商品() {
        assertEquals(0, MallPricing.points(sku(0, null), commodity(19990, null), NINETY));
    }

    @Test
    @DisplayName("🔴 大额不溢出 —— list × percent 必须走 long")
    void 大额不溢出() {
        /*
         * int 的上限是 21 亿多。2000000000 × 90 = 1800 亿，在 int 里会绕成一个负数，
         * 表现是「一件很贵的商品对白金是负价」，而负价一路走到扣款就是【给用户加分】。
         */
        assertEquals(1_800_000_000, MallPricing.points(sku(2_000_000_000, null), commodity(null, null), NINETY));
    }

    @Test
    @DisplayName("🔴 现金价没有等级折扣重载 —— 只打积分是刻意的")
    void 现金不打折() {
        /*
         * 现金是真钱，打折牵扯支付金额、退款、发票和税务口径。
         * 这条用反射钉住「cash 方法不收 GradeDiscount」，
         * 因为哪天有人顺手加一个重载，上面所有用例一条都不会红。
         */
        for (var m : MallPricing.class.getDeclaredMethods()) {
            if (!"cash".equals(m.getName())) {
                continue;
            }
            for (Class<?> p : m.getParameterTypes()) {
                assertFalse(GradeDiscount.class.equals(p),
                        "cash(...) 收了 GradeDiscount —— 现金打折要先把退款和发票口径想清楚");
            }
        }
    }

    // ------------------------------------------------------------ 单品覆盖价

    @Test
    @DisplayName("🔴 命中覆盖价就不再打折 —— 两个都算一遍等于打了两次折")
    void 覆盖价不叠加折扣() {
        /*
         * 运营配「白金特价 888」的意思是 888，不是 888 再打 9.2 折。
         * 叠加不会报错，只是每一单都比运营以为的少收一点，而对账时看不出来 ——
         * 那个数看起来就像一个正常的折后价。
         */
        GradeDiscount d = withOverrides(Map.of(GradeDiscount.Key.ofCommodity(7L), 888));
        assertEquals(888, MallPricing.points(commodityWithId(7L, 10000), d));
        assertEquals(888, MallPricing.points(sku(null, null), commodityWithId(7L, 10000), d));
    }

    @Test
    @DisplayName("🔴 规格覆盖价优先于商品覆盖价 —— 和 sku 价优先于商品价同一条规则")
    void 规格覆盖优先() {
        /*
         * 倒过来的话，给整个商品配了特价之后，单独给某个规格配的那一条【永远不生效】，
         * 而运营只会看到它好好地存在于列表里。
         */
        GradeDiscount d = withOverrides(Map.of(
                GradeDiscount.Key.ofCommodity(7L), 888,
                new GradeDiscount.Key(7L, 70L), 666));
        assertEquals(666, MallPricing.points(skuWithId(70L, null), commodityWithId(7L, 10000), d));
        // 没配规格覆盖价的那个规格，落回商品覆盖价
        assertEquals(888, MallPricing.points(skuWithId(71L, null), commodityWithId(7L, 10000), d));
    }

    @Test
    @DisplayName("⚠️ 列表页只看得到商品级覆盖价 —— 那里没有「选中哪个规格」这回事")
    void 列表页看不到规格覆盖() {
        GradeDiscount d = withOverrides(Map.of(new GradeDiscount.Key(7L, 70L), 666));
        // 只配了规格覆盖价时，列表页落回折扣率
        assertEquals(9000, MallPricing.points(commodityWithId(7L, 10000), d));
        assertEquals(666, MallPricing.points(skuWithId(70L, null), commodityWithId(7L, 10000), d));
    }

    @Test
    @DisplayName("🔴 覆盖价高于挂牌价时按挂牌价走 —— 高等级不能反而更贵")
    void 覆盖价不许加价() {
        /*
         * 保存那一层拦了，但那是拿【当时】的挂牌价比的。
         * 运营之后把商品调便宜，那一行就悄悄变成了「白金比谁都贵」，
         * 而没有任何地方会再检查一次。
         */
        GradeDiscount d = withOverrides(Map.of(GradeDiscount.Key.ofCommodity(7L), 20000));
        assertEquals(10000, MallPricing.points(commodityWithId(7L, 10000), d));
    }

    @Test
    @DisplayName("覆盖价 0 = 这一档免费；负数按 0 算，不会算出负价")
    void 覆盖价的边界() {
        assertEquals(0, MallPricing.points(commodityWithId(7L, 10000),
                withOverrides(Map.of(GradeDiscount.Key.ofCommodity(7L), 0))));
        // 负价一路走到扣款就是【给用户加分】
        assertEquals(0, MallPricing.points(commodityWithId(7L, 10000),
                withOverrides(Map.of(GradeDiscount.Key.ofCommodity(7L), -500))));
    }

    @Test
    @DisplayName("🔴 商品退出等级折扣时，覆盖价也不生效 —— 一个开关一种含义")
    void 退出开关同时关掉覆盖价() {
        /*
         * 只关折扣率的话，运营关掉开关后发现价格还是变了，
         * 而界面上没有任何东西解释这件事。
         */
        MallCommodity out = optedOut(10000);
        out.setId(7L);
        assertEquals(10000, MallPricing.points(out,
                withOverrides(Map.of(GradeDiscount.Key.ofCommodity(7L), 888))));
    }

    @Test
    @DisplayName("🔴 发给端上的折扣率算的是两个价之比，不是会员的折扣率")
    void 实际折扣率() {
        // 888/10000 → 8.88% → 四舍五入 9 → 端上写「0.9折」
        assertEquals(9, MallPricing.effectivePercent(10000, 888));
        assertEquals(92, MallPricing.effectivePercent(10000, 9200));
        // 没便宜就是 100，端上据此不出角标
        assertEquals(100, MallPricing.effectivePercent(10000, 10000));
        assertEquals(100, MallPricing.effectivePercent(10000, 12000), "算不出加价");
        assertEquals(100, MallPricing.effectivePercent(0, 0), "0 分商品不出角标");
    }

    // ------------------------------------------------ 卡片上该显示哪个价

    /** 带价的在售 SKU */
    private static MallSku onSale(long id, Integer points, String cash) {
        MallSku s = sku(points, cash == null ? null : new BigDecimal(cash));
        s.setId(id);
        return s;
    }

    @Test
    @DisplayName("🔴 卡片发【最便宜那个在售规格】的价，不是商品基准价")
    void 卡片取最便宜的规格() {
        /*
         * 这条对应一个真实的显示错误（2026-09-23 真机验证时发现）：
         * 商品表的 cash_price 是 0，而每个 SKU 都是 5000 —— 卡片直接发基准价，
         * 于是一台实际要 ¥5000 的手机在列表上写着「8,800 积分 + ¥0.00」。
         * 卡片是用户决定要不要点进去的唯一依据，它不能承诺一个结账兑现不了的数。
         */
        MallCommodity c = commodityWithId(1L, 10000);
        c.setCashPrice(BigDecimal.ZERO);

        MallPricing.CardPrice p = MallPricing.cheapest(c,
                List.of(onSale(2L, null, "5000"), onSale(4L, null, "5000")), GradeDiscount.NONE);

        assertEquals(10000, p.points());
        assertEquals(0, new BigDecimal("5000").compareTo(p.cash()), "不能发商品基准价的 0");
        assertFalse(p.varies(), "两个规格同价，不该加「起」");
    }

    @Test
    @DisplayName("各规格不同价时 varies=true —— 端上据此加「起」")
    void 不同价要加起() {
        MallCommodity c = commodityWithId(1L, 10000);

        MallPricing.CardPrice p = MallPricing.cheapest(c,
                List.of(onSale(2L, 9000, null), onSale(4L, 12000, null)), GradeDiscount.NONE);

        assertEquals(9000, p.points(), "取最便宜的那个");
        assertTrue(p.varies());
    }

    @Test
    @DisplayName("🔴 「最便宜」按 (积分, 现金) 依次比，不是各取各的最小值")
    void 最便宜必须是真实存在的规格() {
        /*
         * 各取最小的话会拼出 8800 + ¥100 —— 而没有任何一个规格真的这么卖。
         * 用户点进去选哪个都对不上，那比显示贵一点更糟。
         */
        MallCommodity c = commodityWithId(1L, null);

        MallPricing.CardPrice p = MallPricing.cheapest(c,
                List.of(onSale(2L, 8800, "5000"), onSale(4L, 9000, "100")), GradeDiscount.NONE);

        assertEquals(8800, p.points());
        assertEquals(0, new BigDecimal("5000").compareTo(p.cash()),
                "现金要跟着选中的那个规格走，不能单独取最小");
    }

    @Test
    @DisplayName("积分相同就比现金 —— 结果仍然指向一个真实的规格")
    void 积分相同比现金() {
        MallCommodity c = commodityWithId(1L, null);

        MallPricing.CardPrice p = MallPricing.cheapest(c,
                List.of(onSale(2L, 8800, "5000"), onSale(4L, 8800, "100")), GradeDiscount.NONE);

        assertEquals(0, new BigDecimal("100").compareTo(p.cash()));
        assertTrue(p.varies(), "积分一样但现金不一样，也算不同价");
    }

    @Test
    @DisplayName("🔴 划线价取【同一个】规格的挂牌价，不是所有规格里最高的")
    void 划线价不跨规格() {
        /*
         * 取最高的话，「原价 19,990 / 现价 8,800」这两个数会来自两个不同的规格，
         * 划出来的折扣是编的。
         */
        MallCommodity c = commodityWithId(1L, null);

        MallPricing.CardPrice p = MallPricing.cheapest(c,
                List.of(onSale(2L, 10000, null), onSale(4L, 19990, null)), new GradeDiscount(3, 90, Map.of()));

        assertEquals(9000, p.points(), "10000 × 9 折");
        assertEquals(10000, p.listPoints(), "挂牌价是【那个规格的】10000，不是 19990");
    }

    @Test
    @DisplayName("覆盖价也参与比价 —— 配了特价的规格可能反超成最便宜的那个")
    void 覆盖价参与比价() {
        MallCommodity c = commodityWithId(1L, null);
        GradeDiscount d = withOverrides(Map.of(new GradeDiscount.Key(1L, 4L), 500));

        MallPricing.CardPrice p = MallPricing.cheapest(c,
                List.of(onSale(2L, 10000, null), onSale(4L, 19990, null)), d);

        assertEquals(500, p.points(), "本来更贵的 4 号规格因为覆盖价成了最便宜的");
        assertEquals(19990, p.listPoints());
    }

    @Test
    @DisplayName("⚠️ 一个在售规格都没有时退回商品基准价，不抛也不显示 0")
    void 没有在售规格() {
        MallCommodity c = commodityWithId(1L, 10000);
        c.setCashPrice(new BigDecimal("12.50"));

        MallPricing.CardPrice p = MallPricing.cheapest(c, List.of(), GradeDiscount.NONE);

        assertEquals(10000, p.points());
        assertEquals(0, new BigDecimal("12.50").compareTo(p.cash()));
        assertFalse(p.varies());
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
            /*
             * 🔴 两条路都必须走【带等级折扣的】那个重载。
             * 只调 listPoints 的话价格算出来是对的 —— 只是对所有人都是原价，
             * 而那正是「等级价上线了但没人享受到」的样子：不报错、没日志。
             */
            assertTrue(src.contains("MallPricing.points("),
                    path + " 只用了挂牌价，没走等级折扣");
        }
    }

    /**
     * 🔴 <b>SQL 里也不许直接把商品基准价当成「这件商品多少分」发出去。</b>
     *
     * <p>上面那条守的是 Java，而 2026-09-23 漏网的那一处在 XML 里：
     * {@code MallFavoriteMapper} 的两段排行查询各写了一句
     * {@code c.points_price AS pointsPrice}，完全绕过了 MallPricing。
     * 库里有基准价 99999 而唯一在售规格只要 1000 的商品，于是管理端
     * 「收藏统计」把价格显示成真实值的 <b>100 倍</b> ——
     * 而那一列正是运营判断「定价是不是偏高」的依据。
     *
     * <p>⚠️ 这条只扫这一个文件，不扫全部 XML：{@code MallCommodityMapper} 里的
     * {@code c.points_price} 是<b>对的</b>（那是商品配置本身，管理端编辑页要回显它），
     * {@code MallOrderMapper} 里的是订单快照。
     * 判据是「这个值会不会被当成『用户要付多少』发给人看」，不是「有没有出现这个列名」。
     */
    @Test
    @DisplayName("🔴 收藏排行的价必须取最低在售规格，不许直接发 c.points_price")
    void 收藏排行不发商品基准价() throws IOException {
        String path = "src/main/resources/mapper/mall/MallFavoriteMapper.xml";
        String sql = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        // 注释里会提到这个写法（讲它为什么错），所以先把注释剥掉再判
        String code = sql.replaceAll("(?s)<!--.*?-->", "");
        assertFalse(code.contains("c.points_price              AS pointsPrice"),
                path + " 又把商品基准价当成售价发出去了 —— 要取各在售 SKU 的最低价");
        assertTrue(code.contains("MIN(IFNULL(s2.sku_points_price"),
                path + " 没有取最低在售规格价");
    }
}
