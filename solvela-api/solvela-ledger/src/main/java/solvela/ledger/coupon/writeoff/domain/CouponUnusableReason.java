package solvela.ledger.coupon.writeoff.domain;

import lombok.Getter;

/**
 * 一张券<b>为什么</b>用不了。
 *
 * <h3>🔴 这个枚举存在本身就是一条产品决定</h3>
 * 试算<b>不</b>把用不了的券从结果里筛掉，而是带着原因一起返回。
 *
 * <p>因为用户手里有券却在下单页看不到它，第一反应是<b>系统坏了</b> ——
 * 而真实原因往往只是「没到门槛」或「不适用这个商品」，那一句话能省掉一次客服。
 * 筛掉券的那种实现不报错、不告警，只是让用户困惑，所以永远不会有人去修它。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Getter
public enum CouponUnusableReason {

    /**
     * 这张券<b>没有规则</b>：发它的时候没有对应的券模板。
     *
     * <p>⚠️ 和「减 0 元」是两回事。规则列为 NULL 才是这一档；真配成 0 的券
     * 会正常参与试算并算出 0 —— 那是运营配错，不是系统不知道规则。
     * 两者分得开，靠的是规则列可空（2026-09-15 修掉了阶段 1 那批 NOT NULL DEFAULT）。
     */
    NO_RULE("这张券没有配规则，暂时用不了"),

    /** 没到最低消费门槛。<b>最常见的一档</b>，也是最该让用户看见的一档 */
    BELOW_MIN_AMOUNT("未达到使用门槛"),

    /** 适用范围对不上：指定商品/类目的券用在了别的东西上 */
    SCOPE_MISMATCH("不适用于当前商品或场景"),

    /**
     * 抵扣对象对不上：抵现金的券用在纯积分订单上，反之亦然。
     *
     * <p>🔴 这不是「换算一下就行」—— 1 积分 ≠ 1 元，而汇率是业务定义、还会变。
     * 系统替用户做这个换算，就是替他做了一个没有依据的决定。
     */
    DEDUCT_TARGET_MISMATCH("这张券抵扣的不是当前应付的类型"),
    ;

    private final String desc;

    CouponUnusableReason(String desc) {
        this.desc = desc;
    }
}
