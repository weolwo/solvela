package solvela.mall.pay;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.domain.SystemEnvironment;
import solvela.mall.MallOrder;
import solvela.mall.order.dao.MallOrderDao;
import solvela.mall.order.event.MallOrderPendingEvent;
import solvela.mall.sku.dao.MallSkuDao;
import solvela.marketing.api.MallPayReason;
import solvela.marketing.api.MallPayResult;
import solvela.member.api.CouponWriteOffApi;
import solvela.member.api.CouponWriteOffCmd;
import solvela.member.api.CouponWriteOffView;

/**
 * 「支付」一笔待支付的商城订单。
 *
 * <h3>🔴 今天这是一个<b>假支付</b>：点一下就算付了，不动任何真钱</h3>
 * 全仓没有任何支付网关代码。做它不是为了假装有支付，而是因为
 * <b>{@code POINTS_CASH} 那条路在此之前是一条死路</b>：订单落在 0-待支付，
 * 没有任何东西能把它推到 10-待履约，于是它必然被超时 job 取消。
 * 券也因此在阶段 4 里被禁止用于这类订单 —— 没有地方能把券从「锁定中」推到「已使用」。
 *
 * <h3>🔴 所以它必须<b>不可能</b>跑到生产上</h3>
 * 假支付配到生产，等于「任何人都能把自己的订单标成已支付」—— 白送商品。
 * 拦法照抄短信的 LOG 通道（{@code MemberSmsCodeService.checkTransport}）：
 * <b>启动即失败</b>，不是等第一个真实用户点下去才炸。
 *
 * <h3>状态机上它只做一件事：0-待支付 → 10-待履约</h3>
 * 积分在下单时就已经扣掉了（{@code MallRedeemService}），这里不碰钱包。
 * 真正接了网关之后，网关回调落到的也是这同一个方法 ——
 * 差别只在「凭什么认为付过了」，而不在「付过了之后要做什么」。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallPayService {

    /** 券核销流水里的业务类型。与 MallRedeemService / MallOrderCancelService 一致 */
    private static final String BIZ_TYPE_COUPON = "MALL";

    private final MallOrderDao mallOrderDao;
    private final MallSkuDao mallSkuDao;
    private final CouponWriteOffApi couponWriteOffApi;
    private final ApplicationEventPublisher eventPublisher;
    private final MallPayProperties payProperties;
    private final SystemEnvironment systemEnvironment;

    /**
     * 🔴 生产环境不许用假支付，<b>启动即失败</b>。
     *
     * <p>和短信 LOG 通道那条是同一个理由的不同形态：那边是「日志里躺着验证码」，
     * 这边是「点一下就算付钱了」。两者的共同点是 —— <b>配错了不会有任何异常</b>，
     * 系统会安静地、正确地、一直错下去，直到有人对账。
     *
     * <p>反过来那一半也拦：配了 {@code REAL} 却根本没有网关。
     * 不在这里拦的话，它要等到第一个真实用户点「去支付」才暴露。
     */
    @PostConstruct
    void checkTransport() {
        if (payProperties.getTransport() == MallPayProperties.Transport.DISABLED) {
            // 今天生产环境的真实状态。服务照常启动，只是这条路明确地关着
            log.info("【商城支付】支付通道 DISABLED：POINTS_CASH 订单付不了，"
                    + "会落在待支付并被超时任务取消。这是当前生产环境的预期状态。");
            return;
        }
        if (payProperties.getTransport() == MallPayProperties.Transport.REAL) {
            throw new IllegalStateException(
                    "solvela.mall.pay.transport=REAL，但后端【一行支付网关代码都没有】："
                            + "回调验签、金额核对、幂等落单全都不存在，配成 REAL 只会让"
                            + "所有 POINTS_CASH 订单永远付不掉。接好网关之后再放开这个取值，"
                            + "并把 FAKE 那条分支一起删掉。");
        }
        if (systemEnvironment.isProd()) {
            throw new IllegalStateException(
                    "solvela.mall.pay.transport=FAKE 不允许在生产环境使用："
                            + "它等于「任何人都能把自己的订单标成已支付」，也就是白送商品。"
                            + "生产必须接入真实支付网关。");
        }
        log.warn("【商城支付】当前是 FAKE 通道：点「支付」立刻算成功，<不动任何真钱>。"
                        + "搜关键字【商城假支付】。当前环境 {}。"
                        + "🔴 这个开关只允许在非生产环境使用，配到生产会启动失败。",
                systemEnvironment.getCurrentEnvironment());
    }

    /**
     * 支付。
     *
     * <h3>⚠️ {@code memberId} 由调用方从登录态取</h3>
     * 它进校验条件 —— 少了它就是「可以支付别人的订单」。今天假支付下这只是
     * 帮别人白拿一件商品，接了真网关之后它会变成「用我的钱付别人的单」，
     * 那个方向的错误没人会来报。
     *
     * <h3>🔴 顺序：先抢闸门，再转库存，最后确认券</h3>
     * 闸门（{@code markPaid}）抢不到就什么都别做 —— 那说明超时 job 先到了，
     * 这一单已经取消、积分已经退、券已经放回去。这时候继续往下走会把
     * 一个已取消的订单的库存转成已售。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallPayResult pay(String orderNo, Long memberId) {
        if (payProperties.getTransport() != MallPayProperties.Transport.FAKE) {
            /*
             * 功能没做，不是功能坏了 —— 这两件事对用户是两种提示。
             * 抛异常的话端上会得到一个 500，而那会让人去查日志找一个不存在的故障。
             */
            return MallPayResult.ofReject(MallPayReason.PAY_NOT_AVAILABLE);
        }
        MallOrder order = mallOrderDao.getByOrderNo(orderNo);
        if (order == null || !order.getMemberId().equals(memberId)) {
            // 不存在和不是你的给同一个原因：否则这个接口能用来探测别人的订单号
            return MallPayResult.ofReject(MallPayReason.ORDER_NOT_FOUND);
        }

        if (mallOrderDao.markPaid(orderNo) == 0) {
            /*
             * 抢不到闸门。两种可能，对用户是同一句话「这单已经不能支付了」：
             *   · 超时 job 先到，单子已取消；
             *   · 用户在另一个端上已经付过了（重复提交）。
             *
             * 🔴 不要在这里「幂等成功」：已经是 10-待履约 的单再返回一次成功，
             *    调用方会以为是这次付成功的，于是再投一次履约事件 ——
             *    而履约那一侧的幂等闸（markFulfilling）虽然挡得住，
             *    但这里本来就不该制造那次多余的投递。
             */
            MallOrder current = mallOrderDao.getByOrderNo(orderNo);
            log.info("【商城假支付】{} 抢不到支付闸门，当前状态 {}", orderNo,
                    current == null ? "查不到" : current.getStatus());
            return MallPayResult.ofReject(MallPayReason.ORDER_NOT_PAYABLE);
        }

        confirmStock(order);
        confirmCoupon(order);

        /*
         * 投履约。和下单那条路一样是 AFTER_COMMIT 触发 ——
         * 事务回滚时事件根本不会投递，「单没付成但货已经发出去」在这个形状下不可能发生。
         */
        eventPublisher.publishEvent(new MallOrderPendingEvent(orderNo));

        log.warn("【商城假支付】{} 已标记为已支付（应付现金 {}）。"
                        + "🔴 这是假支付，没有任何真钱进账", orderNo, order.getPayCash());
        return MallPayResult.ofAccepted(orderNo);
    }

    /**
     * 把锁定的库存转成已售。
     *
     * <p>⚠️ 转不动只告警<b>不回滚</b>：闸门已经抢到了，订单已经是待履约。
     * 为库存水位把整笔支付回滚，用户会看到「付了又没付」——
     * 而库存偏低是运营能核对、能修的，那笔支付不是。
     */
    private void confirmStock(MallOrder order) {
        if (order.getSkuId() == null || order.getQuantity() == null || order.getQuantity() <= 0) {
            return;
        }
        if (mallSkuDao.confirmLocked(order.getSkuId(), order.getQuantity()) == 0) {
            log.error("【商城假支付】{} 锁定库存转已售失败，SKU:{} 数量:{}。"
                            + "多半是这一单的锁定已经被超时释放过了，库存水位可能偏高，请人工核对",
                    order.getOrderNo(), order.getSkuId(), order.getQuantity());
        }
    }

    /**
     * 确认券：4-锁定中 → 1-已使用。
     *
     * <h3>🔴 这里就是阶段 4 缺的那个「确认点」</h3>
     * 纯积分单在落单那一刻资产就结清了，所以券在那时确认；
     * {@code POINTS_CASH} 单的资产要到<b>付款</b>才结清 —— 所以它的确认点在这里。
     *
     * <p>阶段 4 之所以直接禁止这类订单用券，正是因为当时没有这个地方：
     * 券会一直挂在锁定中，然后被兜底任务放回去，这一单等于白给了折扣。
     *
     * <p>⚠️ 确认失败只告警不回滚：钱已经算收了、单已经是待履约。
     * 为一张券把整笔支付回滚，是拿一次确定的成功去换一次确定的失败。
     */
    private void confirmCoupon(MallOrder order) {
        if (order.getCouponId() == null) {
            return;
        }
        CouponWriteOffView confirmed = couponWriteOffApi.confirm(new CouponWriteOffCmd(
                order.getCouponId(), BIZ_TYPE_COUPON, order.getOrderNo(), null));
        if (!confirmed.ok()) {
            log.error("【商城假支付】🔴 {} 的券 {} 确认失败：{}。"
                            + "这一单已按抵扣后的金额结算，但券没被标成已使用 —— "
                            + "用户可能把它再用一次，请人工核对",
                    order.getOrderNo(), order.getCouponId(), confirmed.message());
        }
    }
}
