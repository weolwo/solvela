package solvela.app.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 兑换请求。
 *
 * <h3>🔴 刻意没有 memberId，也没有订单号</h3>
 * 会员号由控制器从登录态取 —— 有了那个参数就是「花别人的积分」。
 * 订单号由<b>服务端</b>生成（它同时是扣积分的幂等键），客户端给不了也不该给。
 *
 * @param requestId 幂等键，客户端生成，<b>一次点击一个</b>。
 *                  ⚠️ 它和订单号不是一回事：这个只挡「用户连点两次提交」，
 *                  真正的扣款幂等靠订单号落在
 *                  {@code t_member_asset_transaction} 的唯一键上。
 * @param quantity  件数。不传等于 1。上限在服务端，这里也卡一道 ——
 *                  一个 {@code quantity=99999} 不该走到域里去
 */
public record RedeemRequest(
        @NotBlank(message = "缺少请求标识") String requestId,
        @NotNull(message = "请选择规格") Long skuId,
        @Min(value = 1, message = "兑换件数至少为 1")
        @Max(value = 20, message = "一次最多兑 20 件")
        Integer quantity,
        Long addressId) {

    /** 不传等于 1。校验注解对 null 不生效，所以归一放在这里 */
    public int quantityOrOne() {
        return quantity == null ? 1 : quantity;
    }
}
