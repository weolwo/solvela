package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.ledger.logistic.service.MemberDeliveryService;
import solvela.member.api.DeliveryApi;
import solvela.member.api.DeliveryFillResult;
import solvela.member.api.DeliveryReceiverCmd;
import solvela.member.api.MemberDeliveryView;

import java.util.List;

/**
 * {@link DeliveryApi} 的 HTTP 薄壳。
 *
 * <p>与其余 {@code *InternalController} 同一个形状：{@code implements} 契约接口，
 * 路径与方法只在契约里定义一次。方法体是一行转发，这里不许出现任何业务判断。
 *
 * <h3>⚠️ 它注入的是<b>具体类</b>，不是接口</h3>
 * 本进程里有两个 {@link DeliveryApi} 实现：真实现 {@link MemberDeliveryService}
 * （标了 {@code @Primary}，商城在同进程内直接用它）和本薄壳。
 * 按接口注入会把自己注入进来，变成一次无限递归的自调用。
 * 与 {@code CouponQueryInternalController} 同一个做法。
 *
 * @author alaric
 * @date 2026-09-18
 */
@RestController
@RequiredArgsConstructor
public class DeliveryInternalController implements DeliveryApi {

    private final MemberDeliveryService memberDeliveryService;

    @Override
    public List<MemberDeliveryView> listMine(Long memberId, int limit) {
        return memberDeliveryService.listMine(memberId, limit);
    }

    @Override
    public DeliveryFillResult fillReceiver(DeliveryReceiverCmd cmd) {
        return memberDeliveryService.fillReceiver(cmd);
    }
}
