package solvela.member.api;

/**
 * 发放被拒的原因。
 *
 * <h3>🔴 这里的每一项都表示「别再试了」</h3>
 * 可重试的故障（数据库抖动、网络断）走<b>异常</b>，不走这个枚举 ——
 * 调用方据此区分「把单子标成履约失败，等人来看」和「回滚，等下一轮重试」。
 * 往这里加取值之前先问：这个原因再调一次会有不同结果吗？会的话它不属于这里。
 */
public enum AssetGrantReason {

    /** 资产类型不认识。调用方传了个域里没有的值，是 bug 不是业务情况 */
    UNSUPPORTED_ASSET_TYPE,

    /** COUPON 没给券模编码。发不出去，且重试多少次都一样 —— 商品配置错了 */
    ASSET_REF_REQUIRED,

    /** PHYSICAL 没给收件信息。地址在下单到履约之间被删了，或者调用方压根没传 */
    RECEIVER_REQUIRED,

    /** BALANCE 的面额不是正数 */
    AMOUNT_INVALID,

    MEMBER_NOT_FOUND,

    /** 钱包被冻结。人工介入才能解，重试没用 */
    WALLET_UNAVAILABLE,

    UNKNOWN
}
