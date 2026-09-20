package solvela.member.api;

/**
 * 补填收件信息。<b>三个字段都是明文</b>，由拥有地址的那一侧解析好之后传过来。
 *
 * <p>落库时由 {@code PiiTypeHandler} 加密 —— 调用方不要自己先加密一遍，
 * 那会得到「密文的密文」，读出来是一串谁也看不懂的字符，而且不报错。
 *
 * @param deliveryId      履约单 id
 * @param memberId        会员号。<b>由网关从登录态取</b>，它进校验条件 ——
 *                        少了它就是「可以改别人的收货地址」
 * @param receiverName    收件人姓名
 * @param receiverPhone   收件电话，<b>明文</b>
 * @param receiverAddress 收件详细地址（省市区 + 详址拼好的整串）
 * @author alaric
 * @date 2026-09-18
 */
public record DeliveryReceiverCmd(
        Long deliveryId,
        Long memberId,
        String receiverName,
        String receiverPhone,
        String receiverAddress) {
}
