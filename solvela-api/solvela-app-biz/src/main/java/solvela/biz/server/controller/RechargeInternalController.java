package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.external.service.RechargeApiService;
import solvela.marketing.api.RechargeApi;
import solvela.marketing.api.RechargeOptionsView;
import solvela.marketing.api.RechargeOrderCmd;
import solvela.marketing.api.RechargeOrderResult;
import solvela.marketing.api.RechargeOrderView;
import solvela.marketing.api.RechargeTrialView;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link RechargeApi} 的 HTTP 薄壳，给网关跨进程用。
 *
 * <h3>为什么 implements 接口，而不是自己写 @PostMapping</h3>
 * Spring MVC 认得接口上的 {@code @HttpExchange}，所以<b>路径与方法只在契约里定义一次</b>。
 * 自己再写一遍映射的话，网关侧的客户端代理和这里的服务端映射就是两份，
 * 改一处忘另一处 —— 表现是 404，而且要等到联调才发现。
 *
 * <h3>⚠️ 它注入的是<b>具体类</b>，不是接口</h3>
 * 本进程里有两个 {@link RechargeApi} 实现：真实现 {@link RechargeApiService}
 * 和本薄壳。按接口注入会把自己注入进来，变成一次无限递归的自调用。
 * 与 {@code CouponQueryInternalController} 同一个处境。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@RestController
@RequiredArgsConstructor
public class RechargeInternalController implements RechargeApi {

    private final RechargeApiService rechargeApiService;

    @Override
    public RechargeOptionsView options() {
        return rechargeApiService.options();
    }

    @Override
    public RechargeTrialView trial(Long memberId, BigDecimal amount) {
        return rechargeApiService.trial(memberId, amount);
    }

    @Override
    public RechargeOrderResult create(RechargeOrderCmd cmd) {
        return rechargeApiService.create(cmd);
    }

    @Override
    public RechargeOrderResult pay(String orderNo, Long memberId) {
        return rechargeApiService.pay(orderNo, memberId);
    }

    @Override
    public List<RechargeOrderView> listMyOrders(Long memberId, int limit) {
        return rechargeApiService.listMyOrders(memberId, limit);
    }
}
