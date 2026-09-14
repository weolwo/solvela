package solvela.mall.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.MallOrderStatusEnum;
import solvela.enums.NotificationTemplateEnum;
import solvela.mall.MallCommodity;
import solvela.mall.MallOrder;
import solvela.mall.commodity.dao.MallCommodityDao;
import solvela.mall.exchangelimit.dao.MallExchangeLimitDao;
import solvela.mall.order.dao.MallOrderDao;
import solvela.mall.sku.dao.MallSkuDao;
import solvela.member.api.AssetDebitApi;
import solvela.member.api.AssetDebitCmd;
import solvela.member.api.AssetDebitResult;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.math.BigDecimal;

/**
 * 超时未支付订单的取消与补偿。
 *
 * <h3>它补的是一个真实的资损口子</h3>
 * {@code MallRedeemService.redeem} 里，<b>积分是在建单之前就扣掉的</b>
 * （{@code debitPoints} 在 {@code buildOrder} 之前）。而 {@code POINTS_CASH} 类商品
 * 建出来的单子落在 0-待支付，等现金支付回调 —— 那条链路至今一行代码都没有。
 *
 * <p>于是在本类出现之前：<b>积分扣了、库存锁了、限购占了，然后订单永远停在待支付。</b>
 * {@code t_mall_order.expire_time} 的列注释写着「到点由 job 取消并放回库存」，
 * {@code MallSkuDao.releaseLocked} 的注释写着「到期由超时释放 job 调它放回去」，
 * 而那个 job 一直不存在 —— 两处注释都在描述一个没写出来的东西。
 *
 * <h3>🔴 顺序是设计过的：先闸门，后补偿</h3>
 * <ol>
 *   <li>{@code markCancelled}（带 {@code AND status = 0}）—— <b>并发闸</b>。
 *       用户正在支付、job 同时到点，只有一个能改成这一行；</li>
 *   <li>拿到 0 行就<b>整单放弃</b>，一个补偿都不做。
 *       反过来先退款再取消的话，会给一个刚刚支付成功的订单退积分。</li>
 * </ol>
 *
 * <h3>为什么整段一个事务</h3>
 * 退积分、放库存、放限购三件事必须一起成立。今天 {@code AssetDebitApi} 是<b>进程内</b>
 * 实现（{@code AssetDebitApiService}），会加入本事务，所以中途挂掉整体回滚、
 * 下一轮重跑即可 —— 订单还是 0-待支付，会被重新捞出来。
 *
 * <p>⚠️ <b>资产域真独立成服务的那天，这个前提就没了</b>：退款变成一次 HTTP 往返，
 * 本地回滚回滚不了对面已经退掉的积分。那时要么上 saga / 本地消息表，要么
 * 靠 {@code bizRefId} 的幂等性做补偿重试（退款的 bizRefId 是
 * {@code orderNo:REFUND}，重复提交会被唯一键挡掉 —— 这一点现在就是对的）。
 * 这段注释就是那天要回来看的地方。
 *
 * <h3>🔴 必须独立于 Job 类</h3>
 * 铁律 11：{@code @Transactional} 靠代理生效，同一个 Bean 内部自调用绕过代理、
 * <b>事务静默不开启</b>。Job 里直接写事务方法的话，上面那段「整体回滚」就是摆设。
 * 这和 {@code MallOrderFulfillListener} / {@code MallFulfillService} 分开是同一个理由。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallOrderCancelService {

    /** 与 MallRedeemService 扣分时用的 bizType 保持一致，对账才对得上 */
    private static final String BIZ_TYPE = "MALL_EXCHANGE";

    /**
     * 退款的 bizRefId 后缀。
     *
     * <p>🔴 <b>必须与扣减时的 bizRefId 不同</b>（扣减用的是裸 {@code orderNo}）。
     * 一样的话会被 {@code t_member_asset_transaction} 上的
     * {@code UNIQUE(biz_ref_id, asset_type)} 当成重复提交挡掉 ——
     * 表现是「退款静默不生效」，而且不报错。{@code AssetDebitApi#refund} 的注释
     * 专门警告过这一点。
     *
     * <p>顺带一个好处：加了后缀之后这个键<b>天然幂等</b>，重复退只会被唯一键挡住。
     */
    private static final String REFUND_SUFFIX = ":REFUND";

    private static final String CANCEL_REASON = "超时未支付，自动取消";

    private final MallOrderDao mallOrderDao;
    private final MallSkuDao mallSkuDao;
    private final MallExchangeLimitDao mallExchangeLimitDao;
    private final MallCommodityDao mallCommodityDao;
    private final AssetDebitApi assetDebitApi;
    private final NotificationService notificationService;

    /**
     * 取消一单并补偿。
     *
     * <p>{@code REQUIRES_NEW}：一单一个事务。一单失败不该把同一批里其它单的补偿也回滚掉 ——
     * 那会让一次偶发的脏数据放大成「整批都没处理」。
     *
     * @return 是否真的取消了（false = 已被别人处理，本次什么都没做）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean cancelExpired(MallOrder order) {
        String orderNo = order.getOrderNo();

        // ① 闸门。拿不到就整单放弃 —— 那意味着用户刚支付成功，或者另一个 job 实例先到
        if (mallOrderDao.markCancelled(orderNo, CANCEL_REASON) == 0) {
            log.info("【商城超时取消】{} 已被处理（多半是刚支付成功），本次跳过", orderNo);
            return false;
        }

        // ② 放回锁定库存。POINTS_CASH 走的是 lock 不是 sell，所以放的是 locked_stock
        releaseStock(order);

        // ③ 放回限购额度
        releaseExchangeLimit(order);

        // ④ 退积分
        int refunded = refundPoints(order);

        // ⑤ 告诉用户一声。积分静悄悄退回去的话，用户只会看到订单莫名消失、数字莫名变了
        notificationService.send(NotifyRequest.of(NotificationTemplateEnum.ORDER_CANCELLED, order.getMemberId())
                .param("orderNo", orderNo)
                .param("commodityName", order.getCommodityName())
                .param("refundPoints", refunded)
                .bizRefId(orderNo)
                .build());

        log.info("【商城超时取消】{} 已取消，退还积分 {}，放回库存 {} 件", orderNo, refunded, order.getQuantity());
        return true;
    }

    private void releaseStock(MallOrder order) {
        if (order.getSkuId() == null || order.getQuantity() == null || order.getQuantity() <= 0) {
            return;
        }
        int rows = mallSkuDao.releaseLocked(order.getSkuId(), order.getQuantity());
        if (rows == 0) {
            // locked_stock 已经不够减了 —— 说明有人手工改过库存，或者这一单被放过两次。
            // 不抛异常（订单已经取消了，回滚反而更乱），但必须留一条能被告警抓到的记录
            log.error("【商城超时取消】{} 放回锁定库存失败，SKU:{} 数量:{}。库存水位可能偏低，请人工核对",
                    order.getOrderNo(), order.getSkuId(), order.getQuantity());
        }
    }

    /**
     * 放回限购额度。
     *
     * <p>⚠️ {@code MallExchangeLimitDao.release} 的 period_key 是<b>按 NOW() 算</b>的，
     * 所以只能释放<b>同一个限购周期内</b>占掉的额度。支付超时通常是 15 分钟，
     * 跨周期（跨天/跨周/跨月）的概率极低，但不是零 —— 恰好在 23:59 下单的那一单，
     * 第二天释放时会打到新周期的 key 上，{@code used_count >= quantity} 条件不成立、
     * 影响 0 行。那种情况下用户当天的额度会白占一次。
     *
     * <p>为这个边角去给 {@code t_mall_exchange_limit} 存一列 period_key 快照是可以的，
     * 但那要改扣减那一侧的写入，属于另一个改动。这里先记着。
     */
    private void releaseExchangeLimit(MallOrder order) {
        if (order.getCommodityId() == null || order.getQuantity() == null) {
            return;
        }
        MallCommodity commodity = mallCommodityDao.selectById(order.getCommodityId());
        if (commodity == null || commodity.getLimitCount() == null || commodity.getLimitCount() <= 0) {
            return;
        }
        mallExchangeLimitDao.release(order.getMemberId(), order.getCommodityId(),
                commodity.getLimitPeriod(), order.getQuantity());
    }

    /**
     * 退积分。
     *
     * @return 实际退还的积分数；0 表示这一单本来就没花积分（免费兑换是合法的）
     */
    private int refundPoints(MallOrder order) {
        Integer payPoints = order.getPayPoints();
        if (payPoints == null || payPoints <= 0) {
            return 0;
        }
        AssetDebitResult result = assetDebitApi.refund(new AssetDebitCmd(
                order.getMemberId(),
                "SCORE",
                BigDecimal.valueOf(payPoints),
                BIZ_TYPE,
                order.getOrderNo() + REFUND_SUFFIX,
                "商城订单超时取消退还 " + order.getCommodityName()));

        if (!result.accepted()) {
            // 🔴 这是真资损：单子取消了、库存放了，积分没回来。
            // 抛出去让整个事务回滚 —— 订单退回 0-待支付，下一轮重新处理。
            // 退款本身是幂等的（bizRefId 带 :REFUND 后缀 + 唯一键），重试安全
            throw new IllegalStateException(
                    "订单 " + order.getOrderNo() + " 退还积分失败：" + result.reason() + "，本单整体回滚重试");
        }
        return payPoints;
    }
}
