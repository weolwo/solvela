package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.ledger.coupon.writeoff.CouponQueryApiService;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.CouponTrialQuery;
import solvela.member.api.CouponTrialView;
import solvela.member.api.MemberCouponView;

import java.util.List;

/**
 * {@link CouponQueryApi} 的 HTTP 薄壳，给网关跨进程用。
 *
 * <h3>为什么 implements 接口，而不是自己写 @GetMapping</h3>
 * Spring MVC 认得接口上的 {@code @HttpExchange}，所以<b>路径与方法只在契约里定义一次</b>。
 * 自己再写一遍映射的话，网关侧的客户端代理和这里的服务端映射就是两份，
 * 改一处忘另一处 —— 表现是 404，而且要等到联调才发现。
 * 与 {@link AssetInternalController} 同一个做法。
 *
 * <h3>⚠️ 它注入的是<b>具体类</b>，不是接口</h3>
 * 因为本进程里有两个 {@link CouponQueryApi} 实现：真实现
 * {@link CouponQueryApiService}（标了 {@code @Primary}，商城在同进程内直接用）
 * 和本薄壳。按接口注入会把自己注入进来，变成一次无限递归的自调用。
 *
 * <h3>🔴 只读。核销没有 HTTP 薄壳，也不该有</h3>
 * {@code CouponWriteOffApi} 能直接消耗用户的券。它今天只在同进程内被商城调用，
 * 网关那一侧<b>连代理 bean 都没有</b> —— 这是有意的。
 */
@RestController
@RequiredArgsConstructor
public class CouponQueryInternalController implements CouponQueryApi {

    private final CouponQueryApiService couponQueryApiService;

    @Override
    public List<MemberCouponView> listCoupons(Long memberId, String status) {
        return couponQueryApiService.listCoupons(memberId, status);
    }

    @Override
    public CouponTrialView trial(CouponTrialQuery query) {
        return couponQueryApiService.trial(query);
    }
}
