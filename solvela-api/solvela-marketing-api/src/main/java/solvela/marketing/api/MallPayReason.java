package solvela.marketing.api;

/**
 * 支付被拒的原因。
 *
 * <p>调用方用 switch 表达式接，别写 default —— 新增一种拒绝时编译不过，
 * 比悄悄显示成「操作失败」好。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
public enum MallPayReason {

    /**
     * 订单不存在，<b>或者不是你的</b>。
     *
     * <p>🔴 两种合并成一个原因是刻意的：分开的话这个接口就能用来
     * 探测别人的订单号存不存在。
     */
    ORDER_NOT_FOUND,

    /**
     * 这单已经不能支付了：已经付过、或者已经被超时取消。
     *
     * <p>对用户是同一句话「这单已经不能支付了，请回订单列表看看」——
     * 分得更细也不会改变他的下一步动作。
     */
    ORDER_NOT_PAYABLE,

    /**
     * 支付功能<b>没开</b>（{@code solvela.mall.pay.transport=DISABLED}）。
     *
     * <p>🔴 这是今天生产环境的真实状态：支付网关还没接。
     * 它和「坏了」是两件事，所以单独一档 —— 功能没做该说「暂未开放」，
     * 而不是给用户一个 500 让人去查一个不存在的故障。
     */
    PAY_NOT_AVAILABLE
}
