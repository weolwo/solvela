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
 * <h3>⚠️ 以后要加「等级价」之类的新维度，加在这里</h3>
 * 加在调用方就等于又分叉一次。这个类刻意只有静态方法、不依赖任何 Bean，
 * 就是为了让每一条路都<b>没有理由</b>绕开它。
 *
 * @author alaric
 * @date 2026-09-22
 */
public final class MallPricing {

    private MallPricing() {
    }

    /**
     * 积分价：SKU 填了用 SKU 的，没填继承商品基准价。
     *
     * <p>🔴 DDL 刻意允许 NULL 而非默认 0 —— <b>0 是「免费兑换」的合法取值</b>，
     * 用 0 当「未设置」就再也分不清「没填」和「真免费」了。
     * 所以判空只能判 null，不能判 {@code <= 0}。
     */
    public static int points(MallSku sku, MallCommodity commodity) {
        Integer skuPrice = sku.getSkuPointsPrice();
        if (skuPrice != null) {
            return skuPrice;
        }
        return commodity.getPointsPrice() == null ? 0 : commodity.getPointsPrice();
    }

    /** 现金价：规则与 {@link #points} 完全一致，理由同上 */
    public static BigDecimal cash(MallSku sku, MallCommodity commodity) {
        BigDecimal skuPrice = sku.getSkuCashPrice();
        if (skuPrice != null) {
            return skuPrice;
        }
        return commodity.getCashPrice() == null ? BigDecimal.ZERO : commodity.getCashPrice();
    }
}
