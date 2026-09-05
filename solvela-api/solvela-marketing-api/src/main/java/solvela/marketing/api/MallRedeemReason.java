package solvela.marketing.api;

/**
 * 兑换没被受理的原因。域只给 reason，<b>说什么由接入层决定</b> ——
 * 同一个 {@code OUT_OF_STOCK}，C 端要说「手慢了，已被兑完」，
 * 内部工具要看到的是事实本身。与抽奖那条链路同一套分工。
 *
 * <p>🔴 调用方用 switch 表达式接，<b>别写 default</b>：
 * 这里新增一个值时那边编译不过，而不是悄悄显示成「操作失败」。
 */
public enum MallRedeemReason {

    /** SKU 不存在。用户拿了个过期页面或伪造的 id */
    SKU_NOT_FOUND,

    /** 商品已下架 / 不在上架有效期内。与「不存在」合并，对用户是同一件事 */
    COMMODITY_OFF,

    /** 库存不够（或 SKU 已停用） */
    OUT_OF_STOCK,

    /** 超出限兑次数 */
    EXCHANGE_LIMITED,

    /** 积分不足。接入层应当算出差额告诉用户 */
    POINTS_NOT_ENOUGH,

    /** 钱包被冻结 */
    WALLET_UNAVAILABLE,

    /** 余额并发变动，<b>可重试</b>。别和「积分不足」合并成同一句提示 */
    CONCURRENT_CONFLICT,

    /** 实物商品没传收货地址 */
    ADDRESS_REQUIRED,

    /** 地址不存在或不属于这个会员（软引用，用户可能刚把它删了） */
    ADDRESS_NOT_FOUND,

    /** 我们自己的问题。对用户是「服务出问题了」，不是「你填错了」 */
    INTERNAL
}
