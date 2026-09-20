package solvela.member.api;

/**
 * 补填收件信息的结果。
 *
 * <h3>为什么用返回值而不是抛异常</h3>
 * 「这一单已经发货了，填不了了」是一个<b>完全预期内</b>的结果 —— 用户点开页面到点提交
 * 之间，运营完全可能刚好把它发出去。抛异常的话端上分不清它和「服务挂了」，
 * 而这两件事该说的话不一样。
 *
 * <p>与 {@code MallRedeemResult} / {@code MallPayResult} 同一个形状。
 *
 * @param accepted 填上了没有
 * @param message  给用户看的一句话。成功时也有（「收货信息已保存」），
 *                 端上直接显示，不要自己再拼一句 —— 措辞归服务端
 * @author alaric
 * @date 2026-09-18
 */
public record DeliveryFillResult(boolean accepted, String message) {

    public static DeliveryFillResult ok() {
        return new DeliveryFillResult(true, "收货信息已保存，我们会尽快发货");
    }

    public static DeliveryFillResult reject(String message) {
        return new DeliveryFillResult(false, message);
    }
}
