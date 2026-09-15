package solvela.member.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 券的<b>只读</b>契约：券包与试算。实现在 {@code solvela-ledger}。
 *
 * <h3>🔴 这里只读，不写</h3>
 * 锁定 / 确认 / 释放在 {@link CouponWriteOffApi}，那个<b>不能接到网关上</b>。
 *
 * <p>拆成两个接口而不是一个带权限判断的大接口，是因为「网关不该拿到写能力」
 * 这件事应该由<b>类型系统</b>表达，而不是靠谁记得。
 * {@link AssetApi} / {@link AssetDebitApi} 就是同一个拆法。
 *
 * <h3>试算为什么在只读这一侧</h3>
 * 它不改任何状态，而且<b>下单页就是要调它</b> —— 用户选券之前得先看到
 * 「这张能减多少、那张为什么不能用」。放在写接口里的话，网关要么拿不到它，
 * 要么被迫连着写能力一起拿到。
 */
@HttpExchange("/internal/coupon")
public interface CouponQueryApi {

    /**
     * 我的券包。
     *
     * <p>⚠️ 会员号由<b>调用方从登录态取</b>并放进路径。客户端传的一律不认 ——
     * 否则就是「看别人的券」。与 {@link AssetApi#listAssets} 同一个规矩。
     *
     * @param status 券包的 tab：{@code USABLE}（可用）/ {@code USED}（已使用）/
     *               {@code INVALID}（已过期或已作废）。传别的值返回空列表
     */
    @GetExchange("/wallet/{memberId}")
    List<MemberCouponView> listCoupons(@PathVariable Long memberId, @RequestParam String status);

    /**
     * 试算：这一单能用哪些券、各能减多少、用不了的<b>为什么</b>用不了。
     *
     * <p>不改任何状态。
     *
     * <p>🔴 结果里的不可用券<b>要展示出来</b>，别过滤掉 —— 用户手里有券却在
     * 下单页看不到它，第一反应是系统坏了，而真实原因往往只是「没到门槛」，
     * 那一句话能省掉一次客服。
     */
    @PostExchange("/trial")
    CouponTrialView trial(@RequestBody CouponTrialQuery query);
}
