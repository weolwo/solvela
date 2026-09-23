package solvela.mall.commodity;

import solvela.mall.MallCommodity;
import solvela.mall.MallSku;

import java.math.BigDecimal;

/**
 * 商城定价：<b>一件商品到底要多少分 / 多少钱，只有这一处说了算</b>。
 *
 * <h3>🔴 为什么必须收成一处</h3>
 * 这条规则同时被三条路用：C 端列表与详情（{@code MallClientFacade}）、
 * 下单扣减（{@code MallRedeemService}）、以及将来任何一个新页面。
 * 各写各的后果不是报错，是<b>页面显示的价和实际扣的分对不上</b> ——
 * 而用户只会看到后者，然后来投诉「明明写着 0 分」。
 *
 * <p>2026-09-22 就是这么发现的：facade 把 nullable 的 SKU 价原样发给端上，
 * 于是「继承基准价」这件事跑到了端上，而两个页面只有一个记得做 ——
 * {@code RedeemView.vue} 写的是 {@code ?? 0}，SKU 没填价时兑换页显示 <b>0 分</b>，
 * 服务端照基准价扣。库里当时就有这样一条 SKU，只是库存为 0 才没被点到。
 *
 * <h3>🔴 两个价，名字必须分得开（2026-09-23 加等级价时立的）</h3>
 * <ul>
 *   <li>{@link #listPoints} —— <b>挂牌价</b>。谁来看都一样，落订单快照用它</li>
 *   <li>{@link #points} —— <b>这个人要付的价</b>。吃等级折扣，展示与扣减都用它</li>
 * </ul>
 * 加等级价那天，老的两参 {@code points(sku, commodity)} 是<b>改名</b>而不是保留 ——
 * 保留的话它会变成一个「看起来就是价格」的方法，而任何一处忘了换成三参版本，
 * 表现就是那个页面/那条路悄悄按原价算，和 2026-09-22 那次一模一样。
 * 改名之后，每一个调用点都必须被看一眼，然后才能编译过。
 *
 * <h3>⚠️ 以后要加新的定价维度，还是加在这里</h3>
 * 加在调用方就等于又分叉一次。这个类刻意只有静态方法、不依赖任何 Bean，
 * 就是为了让每一条路都<b>没有理由</b>绕开它。
 *
 * @author alaric
 * @date 2026-09-22
 */
public final class MallPricing {

    /** 折扣率的分母。{@code percent} 是百分比整数，90 = 9 折 */
    private static final int PERCENT_BASE = 100;

    private MallPricing() {
    }

    /**
     * 挂牌积分价：SKU 填了用 SKU 的，没填继承商品基准价。<b>不含等级折扣。</b>
     *
     * <p>🔴 DDL 刻意允许 NULL 而非默认 0 —— <b>0 是「免费兑换」的合法取值</b>，
     * 用 0 当「未设置」就再也分不清「没填」和「真免费」了。
     * 所以判空只能判 null，不能判 {@code <= 0}。
     *
     * <p>⚠️ 展示与扣减<b>都不要直接用它</b>，用 {@link #points}。
     * 它的用途只有两个：落订单快照（{@code t_mall_order.points_price} 是挂牌价），
     * 以及端上那道划线价。
     */
    public static int listPoints(MallSku sku, MallCommodity commodity) {
        Integer skuPrice = sku.getSkuPointsPrice();
        if (skuPrice != null) {
            return skuPrice;
        }
        return listPoints(commodity);
    }

    /**
     * 商品基准挂牌价，<b>不看 SKU</b>。
     *
     * <p>列表页用它 —— 那里一件商品只占一行，没有「选了哪个规格」这回事。
     * ⚠️ 所以列表上的价和详情页选中某个 SKU 之后的价<b>可以不一样</b>，
     * 那是运营给规格配了差价，不是 bug。
     */
    public static int listPoints(MallCommodity commodity) {
        if (commodity == null || commodity.getPointsPrice() == null) {
            return 0;
        }
        return commodity.getPointsPrice();
    }

    /**
     * 这个人要付的积分价 = 挂牌价 × 等级折扣率。
     *
     * <p>🔴 <b>商品的「参不参与等级折扣」判在这里面</b>，不在调用方。
     * 放到调用方就是两条路各判一次，而漏判的那一条会给一件成本价商品打折 ——
     * 那是真实的钱，且不会有任何报错。
     *
     * <p>⚠️ <b>向下取整</b>。9 折的 10001 分算出来是 9000 而不是 9000.9：
     * 积分是整数，必须选一个方向，选对用户有利的那个。
     * 展示与扣减走的是同一行代码，所以「显示 9000 实扣 9001」这种事结构上不可能发生。
     */
    public static int points(MallSku sku, MallCommodity commodity, GradeDiscount discount) {
        return apply(listPoints(sku, commodity), commodity, discount);
    }

    /** 商品基准价的折后版，列表页用。规则同 {@link #points(MallSku, MallCommodity, GradeDiscount)} */
    public static int points(MallCommodity commodity, GradeDiscount discount) {
        return apply(listPoints(commodity), commodity, discount);
    }

    private static int apply(int list, MallCommodity commodity, GradeDiscount discount) {
        if (list <= 0 || discount == null || !discount.applies() || !participates(commodity)) {
            return list;
        }
        // long 运算：list 最大到 int 上限，× 100 会溢出
        return (int) ((long) list * discount.percent() / PERCENT_BASE);
    }

    /**
     * 这件商品参不参与等级折扣。
     *
     * <p>⚠️ 兼容 null：{@code grade_price_flag} 是后加的列（2026-09-23），
     * DDL 上 NOT NULL DEFAULT 1，但从别处 new 出来的 {@link MallCommodity}（测试、拷贝）
     * 可能没设值。把 null 当「参与」是和 DDL 默认值一致的方向 ——
     * 反过来会让一件本该有等级价的商品悄悄按原价卖。
     */
    public static boolean participates(MallCommodity commodity) {
        if (commodity == null) {
            return false;
        }
        Integer flag = commodity.getGradePriceFlag();
        return flag == null || flag != 0;
    }

    /**
     * 现金价：SKU 填了用 SKU 的，没填继承商品基准价。
     *
     * <p>🔴 <b>现金不吃等级折扣，所以这里没有 {@link GradeDiscount} 参数。</b>
     * 积分是平台自己发的，打折是自己的事；现金是真钱，打折牵扯支付金额、退款、
     * 发票和税务口径。要改这条，改的不是这个方法，是先把那几件事想清楚。
     */
    public static BigDecimal cash(MallSku sku, MallCommodity commodity) {
        BigDecimal skuPrice = sku.getSkuCashPrice();
        if (skuPrice != null) {
            return skuPrice;
        }
        return commodity.getCashPrice() == null ? BigDecimal.ZERO : commodity.getCashPrice();
    }
}
