package solvela.external.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import solvela.enums.ExternalOrderStatusEnum;
import solvela.external.ExternalOrder;
import solvela.external.ExternalSceneProperties;
import solvela.external.dao.ExternalOrderDao;
import solvela.external.domain.RechargeCmd;
import solvela.external.domain.RechargeResult;
import solvela.marketing.api.RechargeApi;
import solvela.marketing.api.RechargeOptionsView;
import solvela.marketing.api.RechargeOrderCmd;
import solvela.marketing.api.RechargeOrderResult;
import solvela.marketing.api.RechargeOrderView;
import solvela.marketing.api.RechargeTrialView;
import solvela.member.api.CouponTrialView;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@link RechargeApi} 的实现：把充值场景暴露给网关。
 *
 * <h3>它只做一件事：翻译</h3>
 * 业务逻辑全在 {@link ExternalRechargeService} 里。这一层存在的理由是
 * <b>契约里不能出现域内类型</b> —— 契约将来要跨进程，而
 * {@code ExternalOrder} / {@code RechargeReason} 是本域的内部形状。
 *
 * <h3>🔴 明文手机号不出网关</h3>
 * 列表只下发 {@code targetMasked}。要看全号得走另一条带审计的路，
 * 而那条路今天没有 —— 没有比「顺手给出去」好。
 *
 * <h3>🔴 {@code @Primary}：本进程里有两个 {@link RechargeApi} 实现</h3>
 * 本类（真实现）和 {@code RechargeInternalController}（HTTP 薄壳，给网关用）。
 * 薄壳也 implements 这个接口是为了让路径只在契约里定义一次；
 * 代价是按接口注入会歧义，{@code @Primary} 指定「同进程调用拿真实现」。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RechargeApiService implements RechargeApi {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 一次最多回几条。列表页不需要更多，而它是按 member_id 索引扫的 */
    private static final int MAX_LIMIT = 50;

    private final ExternalRechargeService externalRechargeService;
    private final ExternalOrderDao externalOrderDao;
    private final ExternalSceneProperties sceneProperties;

    @Override
    public RechargeOptionsView options() {
        return new RechargeOptionsView(
                sceneProperties.getTransport() != ExternalSceneProperties.Transport.DISABLED,
                sceneProperties.getSceneCode(),
                sceneProperties.getFaceValues(),
                sceneProperties.getMinAmount());
    }

    @Override
    public RechargeTrialView trial(Long memberId, BigDecimal amount) {
        CouponTrialView view = externalRechargeService.trial(memberId, amount);
        return new RechargeTrialView(
                view.allUsable().stream().map(RechargeApiService::toItem).toList(),
                view.unusable().stream().map(RechargeApiService::toItem).toList());
    }

    @Override
    public RechargeOrderResult create(RechargeOrderCmd cmd) {
        RechargeResult result = externalRechargeService.create(new RechargeCmd(
                cmd.memberId(), cmd.targetAccount(), cmd.amount(), cmd.couponId()));
        return toResult(result);
    }

    @Override
    public RechargeOrderResult pay(String orderNo, Long memberId) {
        return toResult(externalRechargeService.payAndExecute(orderNo, memberId));
    }

    @Override
    public List<RechargeOrderView> listMyOrders(Long memberId, int limit) {
        int safeLimit = limit < 1 ? 10 : Math.min(limit, MAX_LIMIT);
        return externalOrderDao.selectMyOrders(memberId, safeLimit).stream()
                .map(RechargeApiService::toView)
                .toList();
    }

    private static RechargeTrialView.Item toItem(CouponTrialView.Item item) {
        return new RechargeTrialView.Item(item.couponId(), item.couponName(),
                item.discountAmount(), item.usable(), item.reasonDesc());
    }

    private static RechargeOrderResult toResult(RechargeResult result) {
        return new RechargeOrderResult(result.accepted(), result.orderNo(), result.payAmount(),
                result.reason() == null ? null : result.reason().name());
    }

    private static RechargeOrderView toView(ExternalOrder order) {
        ExternalOrderStatusEnum status = order.getStatus();
        return new RechargeOrderView(
                order.getOrderNo(),
                // 🔴 只给打码值。明文手机号不出网关
                order.getTargetMasked(),
                order.getOriginalAmount(),
                order.getCouponDiscount(),
                order.getPayAmount(),
                status == null ? null : status.getValue(),
                status == null ? null : status.getDesc(),
                // 能不能支付由服务端判 —— 端上按状态自己推的话，状态机改一次
                // 就会有一个端开始给出错的按钮
                ExternalOrderStatusEnum.UNPAID == status,
                order.getCreateTime() == null ? null : order.getCreateTime().format(DATE_TIME));
    }
}
