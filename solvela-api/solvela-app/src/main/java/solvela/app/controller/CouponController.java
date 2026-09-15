package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.CouponTrialRequest;
import solvela.app.service.CouponService;
import solvela.member.api.CouponTrialView;
import solvela.member.api.MemberCouponView;

import java.util.List;

/**
 * 我的券包 + 下单选券。
 *
 * <h3>没有 @Anonymous，这是刻意的</h3>
 * 默认需要登录，公开页才显式开口子。反过来写的话，新加端点忘了标记就是默默裸奔 ——
 * 券包泄露的是「这个人有哪些券、值多少钱」。与 {@link AssetController} 同一个规矩。
 *
 * <h3>会员号从登录态取，客户端传的一律不认</h3>
 * 接口上<b>没有 memberId 参数</b>，也不该有。有了它就等于开放「查任意会员的券包」。
 *
 * <h3>🔴 这里只能读和试算，不能核销</h3>
 * 锁定 / 确认 / 释放走的是 {@code CouponWriteOffApi}，那个<b>不接到网关上</b>。
 * 券什么时候被消耗掉，只能由下单那条链路决定 —— 开一个「用券」的公网端点，
 * 等于让客户端自己决定券什么时候没的。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@Tag(name = "我的券包")
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * 券包。三个 tab：{@code USABLE} / {@code USED} / {@code INVALID}。
     *
     * <p>一张券都没有时返回<b>空数组</b> —— 新用户就是这个状态，不是 404。
     */
    @GetMapping
    public List<MemberCouponView> listCoupons(@RequestParam(defaultValue = "USABLE") String status) {
        return couponService.listCoupons(CurrentMember.require().memberId(), status);
    }

    /**
     * 下单页选券：这一单能用哪些券、各能减多少。
     *
     * <p>🔴 返回里<b>包含用不了的券和原因</b>，前端要把它们也显示出来（置灰 + 一句原因）。
     * 过滤掉的话，用户手里有券却在下单页看不到它，第一反应是系统坏了 ——
     * 而真实原因往往只是「没到门槛」。
     *
     * <p>⚠️ 试算结果<b>不是承诺</b>：从试算到提交之间，这张券可能被用户在另一个
     * 端上用掉。真正定下来的是下单时服务端重新试算并锁定的那一次 ——
     * 所以下单接口<b>不接受</b>客户端传的抵扣额。
     */
    @PostMapping("/trial")
    public CouponTrialView trial(@RequestBody @Valid CouponTrialRequest request) {
        return couponService.trial(CurrentMember.require().memberId(), request);
    }
}
