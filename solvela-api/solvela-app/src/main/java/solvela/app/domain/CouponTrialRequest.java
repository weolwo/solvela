package solvela.app.domain;

import jakarta.validation.constraints.NotNull;

/**
 * 下单页选券的试算请求。
 *
 * <h3>🔴 刻意没有 memberId，也没有「抵扣多少」</h3>
 * 会员号由控制器从登录态取 —— 有了那个参数就是「看别人的券包」。
 * 抵扣额由服务端算：让客户端报数就是一个可以直接刷钱的口子，
 * 而且下单时服务端还会<b>再算一次</b>，客户端这个数本来也不作数。
 *
 * <p>⚠️ commodityId 由客户端传，服务端<b>据此重新查商品拿价格</b>，不信客户端的价。
 * 传错的后果只是「试算出了别的商品的结果」—— 而试算本来就不是承诺，
 * 真正作数的是下单时服务端自己按 skuId 查出来再算的那一次。
 *
 * @param commodityId 商品 id。详情页手上本来就有
 * @param skuId       要兑的 SKU。门槛按它的积分价判
 * @param quantity    件数。不传等于 1 —— 门槛是按<b>整单</b>判的，件数会影响能不能用券
 */
public record CouponTrialRequest(
        @NotNull(message = "缺少商品") Long commodityId,
        @NotNull(message = "请选择规格") Long skuId,
        Integer quantity) {

    /** 不传等于 1。校验注解对 null 不生效，所以归一放在这里 */
    public int quantityOrOne() {
        return quantity == null || quantity < 1 ? 1 : quantity;
    }
}
