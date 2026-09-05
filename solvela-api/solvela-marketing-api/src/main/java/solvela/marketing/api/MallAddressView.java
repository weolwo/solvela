package solvela.marketing.api;

/**
 * 一条收货地址。
 *
 * <h3>🔴 三列密文、省市区明文，这是刻意的</h3>
 * {@code receiverName} / {@code receiverPhone} / {@code detailAddress} 在库里是密文
 *（AES-256-GCM，与 {@code t_physical_delivery} <b>同一套 PiiTypeHandler、同一把密钥</b> ——
 * 两边各写一套加密，将来「同一个地址在两张表里对不上」会非常难查）。
 * 省市区不加密：识别不到具体自然人，但后台发货分布与物流成本统计要用，
 * 一起加密的话那些统计就全废了。
 *
 * @param receiverPhone <b>脱敏值</b>（138****8000）。解密后在应用层截，
 *                      不存第二份明文脱敏值 —— 存两份就又回到「同一份个人信息存两处」
 */
public record MallAddressView(
        Long addressId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        boolean isDefault) {
}
