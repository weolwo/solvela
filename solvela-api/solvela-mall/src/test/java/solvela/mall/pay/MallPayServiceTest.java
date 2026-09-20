package solvela.mall.pay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import solvela.base.domain.SystemEnvironment;
import solvela.base.enumeration.SystemEnvironmentEnum;
import solvela.enums.MallOrderStatusEnum;
import solvela.mall.order.event.MallOrderActionPublisher;
import solvela.mall.MallOrder;
import solvela.mall.order.dao.MallOrderDao;
import solvela.mall.order.event.MallOrderPendingEvent;
import solvela.mall.sku.dao.MallSkuDao;
import solvela.marketing.api.MallPayReason;
import solvela.marketing.api.MallPayResult;
import solvela.member.api.CouponWriteOffApi;
import solvela.member.api.CouponWriteOffCmd;
import solvela.member.api.CouponWriteOffView;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 假支付。
 *
 * <h3>🔴 这里最要紧的一条不是业务，是「它不可能跑到生产上」</h3>
 * 假支付等于「任何人都能把自己的订单标成已支付」。而它配错了<b>不会有任何异常</b>：
 * 系统会安静地、正确地、一直错下去，直到有人对账。
 * 所以那条启动守卫必须有测试钉着 —— 它是这个类存在的前提。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MallPayServiceTest {

    private static final String ORDER_NO = "M20260915120000123ABC";
    private static final Long MEMBER_ID = 1001L;
    private static final Long COUPON_ID = 777L;

    @Mock
    private MallOrderDao mallOrderDao;
    @Mock
    private MallSkuDao mallSkuDao;
    @Mock
    private CouponWriteOffApi couponWriteOffApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    /**
     * 打点：这一单付掉了。混合单的 ORDER_PAID 产生在这里 ——
     * 另一半（纯积分单）在 {@code MallRedeemServiceTest} 里钉。
     */
    @Mock
    private MallOrderActionPublisher orderActionPublisher;

    private MallPayService service;

    @BeforeEach
    void setUp() {
        service = service(MallPayProperties.Transport.FAKE, SystemEnvironmentEnum.DEV);
        when(mallOrderDao.getByOrderNo(anyString())).thenReturn(unpaidOrder());
        when(mallOrderDao.markPaid(anyString())).thenReturn(1);
        when(mallSkuDao.confirmLocked(anyLong(), anyInt())).thenReturn(1);
        when(couponWriteOffApi.confirm(any()))
                .thenReturn(new CouponWriteOffView(true, false, new BigDecimal("10"), null));
    }

    /* ---------------- 生产守卫 ---------------- */

    @Test
    @DisplayName("🔴 生产环境 + 假支付 → 启动就失败，不是等第一个用户点下去才炸")
    void 生产不许用假支付() {
        MallPayService prod = service(MallPayProperties.Transport.FAKE, SystemEnvironmentEnum.PROD);

        IllegalStateException e = assertThrows(IllegalStateException.class, prod::checkTransport);
        // 配到生产等于白送商品，而且不会有任何异常告诉你
        assertTrue(e.getMessage().contains("不允许在生产环境使用"));
    }

    @Test
    @DisplayName("🔴 反过来那一半也拦：配了 REAL 却根本没有支付网关")
    void 配了真支付却没有网关() {
        MallPayService real = service(MallPayProperties.Transport.REAL, SystemEnvironmentEnum.DEV);

        // 不在启动时拦的话，它要等到第一个真实用户点「去支付」才暴露
        assertThrows(IllegalStateException.class, real::checkTransport);
    }

    @Test
    @DisplayName("非生产 + 假支付：放行，但日志里要喊出来")
    void 非生产放行() {
        service(MallPayProperties.Transport.FAKE, SystemEnvironmentEnum.DEV).checkTransport();
        service(MallPayProperties.Transport.FAKE, SystemEnvironmentEnum.TEST).checkTransport();
    }

    @Test
    @DisplayName("🔴 默认值是 DISABLED —— 最危险的那个值必须显式打开")
    void 默认不开假支付() {
        // 反过来（默认 FAKE）的话，任何一个忘了配这一项的环境都会静默拥有
        // 「点一下就算付钱」的能力，而忘配是常态不是意外
        assertEquals(MallPayProperties.Transport.DISABLED, new MallPayProperties().getTransport());
    }

    @Test
    @DisplayName("🔴 DISABLED 下生产照常启动 —— 支付没接是真实状态，不是错误")
    void 关闭时生产能启动() {
        // 拦成启动失败的话，整个服务会因为一个还没做的功能而起不来
        service(MallPayProperties.Transport.DISABLED, SystemEnvironmentEnum.PROD).checkTransport();
    }

    @Test
    @DisplayName("DISABLED 下点支付 → 「暂未开放」，不是 500")
    void 关闭时如实拒绝() {
        MallPayService off = service(MallPayProperties.Transport.DISABLED, SystemEnvironmentEnum.DEV);

        MallPayResult result = off.pay(ORDER_NO, MEMBER_ID);

        assertAll(
                () -> assertFalse(result.accepted()),
                // 功能没做和功能坏了是两件事，也该是两种提示
                () -> assertEquals(MallPayReason.PAY_NOT_AVAILABLE, result.reason()),
                () -> verify(mallOrderDao, never()).markPaid(anyString()));
    }

    /* ---------------- 支付 ---------------- */

    @Test
    @DisplayName("支付成功：0-待支付 → 10-待履约，锁定库存转已售，投履约事件")
    void 支付走完全程() {
        MallPayResult result = service.pay(ORDER_NO, MEMBER_ID);

        assertTrue(result.accepted());
        verify(mallOrderDao).markPaid(ORDER_NO);
        // 锁定转已售必须发生：POINTS_CASH 下单时走的是 lock 不是 sell
        verify(mallSkuDao).confirmLocked(1L, 1);
        verify(eventPublisher).publishEvent(new MallOrderPendingEvent(ORDER_NO));
    }

    @Test
    @DisplayName("🔴 这里就是阶段 4 缺的确认点：付款那一刻把券从锁定中推到已使用")
    void 支付时确认券() {
        MallPayResult result = service.pay(ORDER_NO, MEMBER_ID);

        ArgumentCaptor<CouponWriteOffCmd> captor = ArgumentCaptor.forClass(CouponWriteOffCmd.class);
        verify(couponWriteOffApi).confirm(captor.capture());
        assertAll(
                () -> assertTrue(result.accepted()),
                // bizRefId 必须是订单号：条件更新是 WHERE locked_biz_id = ?
                () -> assertEquals(ORDER_NO, captor.getValue().bizRefId()));
    }

    @Test
    @DisplayName("🔴 抢不到支付闸门（超时 job 先到 / 已经付过）→ 拒绝，什么都不做")
    void 抢不到闸门就整单放弃() {
        when(mallOrderDao.markPaid(anyString())).thenReturn(0);

        MallPayResult result = service.pay(ORDER_NO, MEMBER_ID);

        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(MallPayReason.ORDER_NOT_PAYABLE, result.reason()),
                /*
                 * 继续往下走的话，一个【已经被超时取消】的订单会被转成已售库存、
                 * 券会被确认掉 —— 而那一单的积分已经退了、券已经放回去了。
                 */
                () -> verify(mallSkuDao, never()).confirmLocked(anyLong(), anyInt()),
                () -> verify(couponWriteOffApi, never()).confirm(any()),
                () -> verify(eventPublisher, never()).publishEvent(any(MallOrderPendingEvent.class)));
    }

    @Test
    @DisplayName("🔴 不能支付别人的订单 —— memberId 进校验条件")
    void 不能支付别人的订单() {
        MallPayResult result = service.pay(ORDER_NO, 9999L);

        assertAll(
                () -> assertFalse(result.accepted()),
                // 不存在和不是你的给同一个原因，否则这个接口能用来探测别人的订单号
                () -> assertEquals(MallPayReason.ORDER_NOT_FOUND, result.reason()),
                () -> verify(mallOrderDao, never()).markPaid(anyString()));
    }

    @Test
    @DisplayName("订单不存在 → 同一个原因，不泄露「这个单号存在」")
    void 订单不存在() {
        when(mallOrderDao.getByOrderNo(anyString())).thenReturn(null);

        assertEquals(MallPayReason.ORDER_NOT_FOUND, service.pay(ORDER_NO, MEMBER_ID).reason());
    }

    @Test
    @DisplayName("没用券的单子不该碰券的接口")
    void 没用券就不碰券接口() {
        MallOrder noCoupon = unpaidOrder();
        noCoupon.setCouponId(null);
        when(mallOrderDao.getByOrderNo(anyString())).thenReturn(noCoupon);

        service.pay(ORDER_NO, MEMBER_ID);

        verify(couponWriteOffApi, never()).confirm(any());
    }

    @Test
    @DisplayName("⚠️ 库存转不动 / 券确认不了 → 只告警，支付照样成功")
    void 后续失败不回滚支付() {
        when(mallSkuDao.confirmLocked(anyLong(), anyInt())).thenReturn(0);
        when(couponWriteOffApi.confirm(any()))
                .thenReturn(new CouponWriteOffView(false, false, null, "券不在本单的锁定中状态"));

        // 闸门已经抢到了、订单已经是待履约。为库存水位或一张券把整笔支付回滚，
        // 用户会看到「付了又没付」—— 而那两样是运营能核对能修的
        assertTrue(service.pay(ORDER_NO, MEMBER_ID).accepted());
        verify(eventPublisher).publishEvent(new MallOrderPendingEvent(ORDER_NO));
    }

    /* ---------------- fixtures ---------------- */

    private MallPayService service(MallPayProperties.Transport transport, SystemEnvironmentEnum env) {
        MallPayProperties properties = new MallPayProperties();
        properties.setTransport(transport);
        return new MallPayService(mallOrderDao, mallSkuDao, couponWriteOffApi, eventPublisher,
                properties, new SystemEnvironment(env == SystemEnvironmentEnum.PROD, "solvela", env),
                orderActionPublisher);
    }

    private static MallOrder unpaidOrder() {
        MallOrder order = new MallOrder();
        order.setOrderNo(ORDER_NO);
        order.setMemberId(MEMBER_ID);
        order.setSkuId(1L);
        order.setQuantity(1);
        order.setStatus(MallOrderStatusEnum.UNPAID);
        order.setPayPoints(5000);
        order.setPayCash(new BigDecimal("40.00"));
        order.setCouponId(COUPON_ID);
        order.setCouponDiscount(new BigDecimal("10.00"));
        return order;
    }
}
