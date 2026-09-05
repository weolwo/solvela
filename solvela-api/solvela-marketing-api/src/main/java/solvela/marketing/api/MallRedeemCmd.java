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
 */
public record MallRedeemCmd(
        Long memberId,
        Long skuId,
        Integer quantity,
        Long addressId) {
}
