package solvela.marketing.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 充值场景的可选项。
 *
 * @param enabled     场景开着没有。🔴 关着时端上要如实显示「暂未开放」，
 *                    <b>不要把入口藏起来</b> —— 藏起来用户会以为是自己的问题
 * @param sceneCode   场景码
 * @param faceValues  可选面额。<b>只认这个白名单</b>，不接受任意金额
 * @param minAmount   最低充值金额。⚠️ 它和「券的门槛」是两回事
 */
public record RechargeOptionsView(boolean enabled,
                                  String sceneCode,
                                  List<BigDecimal> faceValues,
                                  BigDecimal minAmount) {
}
