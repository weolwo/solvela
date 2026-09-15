package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.RechargeRequest;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.marketing.api.RechargeApi;
import solvela.marketing.api.RechargeOptionsView;
import solvela.marketing.api.RechargeOrderCmd;
import solvela.marketing.api.RechargeOrderResult;
import solvela.marketing.api.RechargeOrderView;
import solvela.marketing.api.RechargeTrialView;

import java.math.BigDecimal;
import java.util.List;

/**
 * 充话费。
 *
 * <h3>它是券的第一个<b>非商城</b>出口</h3>
 * 方案 §6.2：充话费这个能力本身不在券方案范围内（要接运营商），
 * 这一页要证明的是「券能被一个外部场景消费掉」这条路是通的。
 *
 * <h3>⚠️ 今天运营商那一端是假的</h3>
 * 下单、扣券、标成功，<b>但话费不会到账</b>。假充值配到生产会让 app-biz
 * <b>启动失败</b> —— 那道闸在域里，这一层不做任何环境判断，
 * 判断散在两处的话总有一处会忘。
 *
 * <h3>没有 @Anonymous，这是刻意的</h3>
 * 默认需要登录。会员号从登录态取，接口上<b>没有 memberId 参数</b> ——
 * 有了它就是「用别人的券给自己充话费」。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@Slf4j
@Tag(name = "充话费")
@RestController
@RequestMapping("/recharge")
@RequiredArgsConstructor
public class RechargeController {

    private final RechargeApi rechargeApi;

    /**
     * 可选面额与最低金额。
     *
     * <p>🔴 场景关着时 {@code enabled = false}，端上要<b>如实显示「暂未开放」</b>，
     * 不要把入口藏起来 —— 藏起来用户会以为是自己的问题。
     */
    @GetMapping("/options")
    public RechargeOptionsView options() {
        return rechargeApi.options();
    }

    /**
     * 选券：这一笔充值能用哪些券。
     *
     * <p>🔴 返回里<b>包含用不了的券和原因</b>，端上要把它们也显示出来（置灰 + 一句原因）。
     */
    @PostMapping("/trial")
    public RechargeTrialView trial(@RequestParam BigDecimal amount) {
        return rechargeApi.trial(CurrentMember.require().memberId(), amount);
    }

    /** 下单。抵扣额由服务端重新试算，请求里<b>没有</b>「减多少」这个字段 */
    @PostMapping("/order")
    public RechargeOrderResult create(@RequestBody @Valid RechargeRequest request) {
        RechargeOrderResult result = rechargeApi.create(new RechargeOrderCmd(
                CurrentMember.require().memberId(), request.targetAccount(),
                request.amount(), request.couponId()));
        if (!result.accepted()) {
            throw translate(result.reason());
        }
        return result;
    }

    /** 支付并执行。⚠️ 今天两者都是假的，所以是一个动作 */
    @PostMapping("/order/{orderNo}/pay")
    public RechargeOrderResult pay(@PathVariable String orderNo) {
        RechargeOrderResult result = rechargeApi.pay(orderNo, CurrentMember.require().memberId());
        if (!result.accepted()) {
            throw translate(result.reason());
        }
        return result;
    }

    /** 我的充值记录，新的在前 */
    @GetMapping("/order")
    public List<RechargeOrderView> listMyOrders() {
        return rechargeApi.listMyOrders(CurrentMember.require().memberId(), 20);
    }

    /**
     * 拒绝原因 → 给用户看的话。
     *
     * <p>⚠️ 这里按<b>字符串</b>接而不是枚举：契约里那一列是字符串，
     * 因为它将来要跨进程，而枚举值域的变更在两边不是同时发版的。
     * 代价是这里没有编译期的穷举检查 —— 所以兜底分支要<b>留痕</b>，
     * 不能静默变成「操作失败」。
     */
    private ApiException translate(String reason) {
        return switch (reason == null ? "" : reason) {
            // 功能没做，不是坏了 —— 如实说，别让用户以为是自己操作有问题
            case "SCENE_NOT_AVAILABLE" ->
                    new ApiException(ApiErrors.CONFLICT, "充话费暂未开放，敬请期待");
            case "BAD_TARGET" -> new ApiException(ApiErrors.CONFLICT, "请填写正确的手机号");
            case "BAD_AMOUNT" -> new ApiException(ApiErrors.CONFLICT, "请选择可用的充值面额");
            case "BELOW_MIN_AMOUNT" -> new ApiException(ApiErrors.CONFLICT, "低于最低充值金额");
            case "COUPON_UNUSABLE" ->
                    new ApiException(ApiErrors.CONFLICT, "这张券用不了了，请换一张或不使用优惠券");
            case "ORDER_NOT_FOUND" -> new ApiException(ApiErrors.NOT_FOUND, "订单不存在");
            case "ORDER_NOT_PAYABLE" ->
                    new ApiException(ApiErrors.CONFLICT, "这单已经不能支付了，请回记录里看看");
            default -> {
                // 域侧加了新的拒绝原因而这里没跟上。必须留痕，否则它会一直静默下去
                log.error("【充话费】未归类的拒绝原因 {} —— 域侧多半加了新枚举，这里要跟上", reason);
                yield new ApiException(ApiErrors.CONFLICT, "暂时无法充值，请稍后再试");
            }
        };
    }
}
