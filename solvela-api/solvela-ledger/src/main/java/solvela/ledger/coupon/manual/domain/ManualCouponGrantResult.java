package solvela.ledger.coupon.manual.domain;

import java.util.List;

/**
 * 人工发券的结果。
 *
 * <h3>🔴 要分得出「发出去了」「本来就发过」「没发成」三种</h3>
 * 只回一个成功数的话，运营看到「成功 3」分不清是这次发的还是上次已经发过的 ——
 * 于是他会再点一次，而这次是真的重复了。
 *
 * @param granted    这次真发出去的张数
 * @param skipped    因为<b>已经发过</b>（同一个工单号）被跳过的会员。这是幂等，不是失败
 * @param failed     没发成的会员。走到这里的都是意外，需要人看
 * @param couponName 发的是什么券，回显给运营确认
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
public record ManualCouponGrantResult(int granted,
                                      List<Long> skipped,
                                      List<Long> failed,
                                      String couponName) {
}
