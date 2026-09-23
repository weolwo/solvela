package solvela.mall.commodity;

import java.util.Map;

/**
 * 一个人在商城享受的<b>等级价</b>：普惠折扣率 + 单品覆盖价。
 *
 * <h3>🔴 为什么是一个类型，不是一个 int</h3>
 * {@link MallPricing#points} 需要知道折扣率。如果签名收的是裸 {@code int}，
 * 那么下面这些调用<b>全都编译得过</b>，而且长得都一样：
 * <pre>
 *   MallPricing.points(sku, commodity, memberGrade);   // 传了等级，不是折扣率
 *   MallPricing.points(sku, commodity, quantity);      // 传了数量
 *   MallPricing.points(sku, commodity, 0);             // 以为「0 = 不打折」，实际是白送
 * </pre>
 * 前两个的后果是价格算成一个荒唐的数（等级 4 → 打 4 折），
 * 第三个是全场白送。三个都不报错。
 *
 * <p>2026-09-23 加覆盖价时，这个理由又兑现了一次：多带一张表的数据进来，
 * 签名一个字都没改，调用方一处都不用动。
 *
 * @param gradeCode 这个人当时的等级，{@code 0} = 无等级 / 未登录。<b>只用于落订单快照</b>，
 *                  不参与算价 —— 算价只看 {@link #percent} 与 {@link #overrides}
 * @param percent   积分折扣率 1-100，{@code 100} = 不打折。
 *                  🔴 <b>取值已经在 {@link MallGradeDiscountResolver} 里夹过</b>，
 *                  这里不再兜底：兜底会把「配置错了」变成「悄悄按原价卖」，
 *                  而那正是最难发现的一种错
 * @param overrides 单品覆盖价，key 见 {@link Key}。<b>命中就不再打折。</b>
 *                  两个都算一遍等于打了两次折，而运营配「白金特价 888」的意思是 888
 * @author alaric
 * @date 2026-09-23
 */
public record GradeDiscount(int gradeCode, int percent, Map<Key, Integer> overrides) {

    /**
     * 覆盖价的键。
     *
     * @param skuId {@code 0} = 整个商品那一行。<b>不是 null</b> —— 理由见
     *              {@code MallGradePrice.skuId}（唯一索引不约束 NULL）
     */
    public record Key(long commodityId, long skuId) {

        /** 整个商品那一行 */
        public static Key ofCommodity(long commodityId) {
            return new Key(commodityId, 0L);
        }
    }

    /** 不打折、也没有覆盖价。未登录、没配折扣率、商品退出等级折扣，最终都落到它 */
    public static final GradeDiscount NONE = new GradeDiscount(0, 100, Map.of());

    public GradeDiscount {
        overrides = overrides == null ? Map.of() : overrides;
    }

    /** 折扣率真的会让价才算数 —— 100 与 NONE 等效。<b>不包括覆盖价</b> */
    public boolean applies() {
        return percent < 100;
    }

    /**
     * 这个 SKU 的覆盖价，没配返回 null。
     *
     * <p>⚠️ 规格覆盖价优先于商品覆盖价 —— 和 {@code sku_points_price} 优先于
     * {@code points_price} 是同一条规则。倒过来的话，给整个商品配了特价之后，
     * 单独给某个规格配的那一条<b>永远不生效</b>，而运营只会看到它存在于列表里。
     */
    public Integer overrideOf(long commodityId, long skuId) {
        if (overrides.isEmpty()) {
            return null;
        }
        if (skuId > 0) {
            Integer bySku = overrides.get(new Key(commodityId, skuId));
            if (bySku != null) {
                return bySku;
            }
        }
        return overrides.get(Key.ofCommodity(commodityId));
    }
}
