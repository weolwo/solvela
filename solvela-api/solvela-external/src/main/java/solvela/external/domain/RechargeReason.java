package solvela.external.domain;

/**
 * 充值被拒的原因。
 *
 * <p>调用方用 switch 表达式接，别写 default —— 新增一种拒绝时编译不过，
 * 比悄悄显示成「操作失败」好。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
public enum RechargeReason {

    /**
     * 场景没开（{@code transport=DISABLED}）。这是今天生产环境的真实状态。
     *
     * <p>🔴 「功能没做」和「功能坏了」是两件事：前者该说「暂未开放」，
     * 后者才是 500。混成一种会让人去查一个不存在的故障。
     */
    SCENE_NOT_AVAILABLE,

    /** 手机号格式不对 */
    BAD_TARGET,

    /** 面额不在白名单里 */
    BAD_AMOUNT,

    /** 低于场景的最低充值金额。⚠️ 它和「券的门槛」是两回事 */
    BELOW_MIN_AMOUNT,

    /** 券用不了：过期、被别的单锁着、已经用掉、或不满足这一单的条件 */
    COUPON_UNUSABLE,

    /** 单据不存在，<b>或者不是你的</b>。合并是刻意的，否则能用来探测别人的单号 */
    ORDER_NOT_FOUND,

    /** 这单已经不能支付了：已经付过、或者已被超时取消 */
    ORDER_NOT_PAYABLE
}
