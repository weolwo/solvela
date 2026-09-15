package solvela.marketing.api;

/**
 * 兑换一件商品。<b>一单一 SKU，没有购物车。</b>
 *
 * @param memberId  会员号，<b>由调用方从登录态取</b>。客户端传的一律不认 ——
 *                  否则就是「花别人的积分」
 * @param skuId     要兑的 SKU
 * @param quantity  件数，至少 1，服务端会封顶
 * @param addressId 收货地址 id。<b>实物必填</b>，虚拟商品传 null。
 *                  ⚠️ 这是软引用：用户可能在下单前把它删了，服务端会重查一次
 * @param couponId  要用的券 id，不用券传 null。
 *                  <p>🔴 <b>抵扣额由服务端重新试算，不信客户端传的数</b> ——
 *                  否则「减多少」就成了客户端说了算，那是一个可以直接刷钱的口子。
 *                  客户端只说「用哪张」，减多少是服务端的事。
 */
public record MallRedeemCmd(
        Long memberId,
        Long skuId,
        Integer quantity,
        Long addressId,
        Long couponId) {

    /** 不用券的兑换。老调用方和测试用它，免得到处补一个 null */
    public static MallRedeemCmd withoutCoupon(Long memberId, Long skuId, Integer quantity, Long addressId) {
        return new MallRedeemCmd(memberId, skuId, quantity, addressId, null);
    }
}
