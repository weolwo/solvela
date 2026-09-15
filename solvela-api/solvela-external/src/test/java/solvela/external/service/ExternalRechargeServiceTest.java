package solvela.external.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.domain.SystemEnvironment;
import solvela.base.enumeration.SystemEnvironmentEnum;
import solvela.enums.ExternalOrderStatusEnum;
import solvela.external.ExternalOrder;
import solvela.external.ExternalSceneProperties;
import solvela.external.dao.ExternalOrderDao;
import solvela.external.domain.RechargeCmd;
import solvela.external.domain.RechargeReason;
import solvela.external.domain.RechargeResult;
import solvela.member.api.CouponLockCmd;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.CouponTrialQuery;
import solvela.member.api.CouponTrialView;
import solvela.member.api.CouponWriteOffApi;
import solvela.member.api.CouponWriteOffView;
import solvela.member.service.MemberService;
import solvela.notification.service.NotificationService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 充话费 —— 券的第一个非商城出口。
 *
 * <h3>这里守的三件事</h3>
 * <ol>
 *   <li><b>假充值不可能上生产</b> —— 它比假支付还危险一档：用户花了钱、券也用了，
 *       系统说充值成功，而话费一分钱都没到账，<b>每一张表看起来都正常</b>；</li>
 *   <li><b>券走的是外部场景那一档</b> —— {@code scope_type = EXTERNAL} 不是摆设；</li>
 *   <li><b>面额只认白名单</b> —— 任意金额是一条本来就不该存在的路。</li>
 * </ol>
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalRechargeServiceTest {

    private static final Long MEMBER_ID = 1001L;
    private static final Long COUPON_ID = 777L;
    private static final String PHONE = "13812348888";

    @Mock
    private ExternalOrderDao externalOrderDao;
    @Mock
    private CouponQueryApi couponQueryApi;
    @Mock
    private CouponWriteOffApi couponWriteOffApi;
    @Mock
    private MemberService memberService;
    @Mock
    private NotificationService notificationService;

    private ExternalRechargeService service;

    @BeforeEach
    void setUp() {
        service = service(ExternalSceneProperties.Transport.FAKE, SystemEnvironmentEnum.DEV);
        when(memberService.requireMemberName(anyLong())).thenReturn("tester");
        when(couponWriteOffApi.lock(any()))
                .thenReturn(new CouponWriteOffView(true, false, new BigDecimal("10"), null));
        when(couponWriteOffApi.confirm(any()))
                .thenReturn(new CouponWriteOffView(true, false, new BigDecimal("10"), null));
        when(couponWriteOffApi.release(any()))
                .thenReturn(new CouponWriteOffView(true, false, null, null));
        stubCouponUsable(new BigDecimal("10"));
    }

    /* ---------------- 生产守卫 ---------------- */

    @Test
    @DisplayName("🔴 生产环境 + 假充值 → 启动就失败")
    void 生产不许用假充值() {
        ExternalRechargeService prod =
                service(ExternalSceneProperties.Transport.FAKE, SystemEnvironmentEnum.PROD);

        IllegalStateException e = assertThrows(IllegalStateException.class, prod::checkTransport);
        // 用户要等到查话费余额才发现，而那时候券已经核销、单据已经是成功态
        assertTrue(e.getMessage().contains("不允许在生产环境使用"));
    }

    @Test
    @DisplayName("🔴 配了 REAL 却没有运营商对接 → 启动就失败")
    void 配了真充值却没有运营商() {
        assertThrows(IllegalStateException.class,
                service(ExternalSceneProperties.Transport.REAL, SystemEnvironmentEnum.DEV)::checkTransport);
    }

    @Test
    @DisplayName("🔴 默认 DISABLED，且 DISABLED 下生产照常启动")
    void 默认关闭且生产能启动() {
        assertEquals(ExternalSceneProperties.Transport.DISABLED,
                new ExternalSceneProperties().getTransport());
        // 一个还没接的场景不该让整个服务起不来
        service(ExternalSceneProperties.Transport.DISABLED, SystemEnvironmentEnum.PROD).checkTransport();
    }

    @Test
    @DisplayName("DISABLED 下下单 → 「暂未开放」，不是 500")
    void 关闭时如实拒绝() {
        ExternalRechargeService off =
                service(ExternalSceneProperties.Transport.DISABLED, SystemEnvironmentEnum.DEV);

        assertEquals(RechargeReason.SCENE_NOT_AVAILABLE,
                off.create(cmd(new BigDecimal("100"), COUPON_ID)).reason());
    }

    /* ---------------- 下单 ---------------- */

    @Test
    @DisplayName("🔴 券走的是【外部场景】那一档：试算带 sceneCode，不带商品/类目")
    void 试算按场景码匹配() {
        service.create(cmd(new BigDecimal("100"), COUPON_ID));

        ArgumentCaptor<CouponTrialQuery> captor = ArgumentCaptor.forClass(CouponTrialQuery.class);
        verify(couponQueryApi).trial(captor.capture());
        CouponTrialQuery query = captor.getValue();
        assertAll(
                // scope_type = EXTERNAL 的券就是按它匹配的 —— 这一档在阶段 1 建出来就是为了这一天
                () -> assertEquals("MOBILE_RECHARGE", query.sceneCode()),
                // 充话费掏的是真钱，没有积分那一侧 —— 积分券在这里本来就无处可抵
                () -> assertEquals(null, query.payPoints()),
                () -> assertEquals(0, new BigDecimal("100").compareTo(query.payCash())),
                // 外部场景没有商品和类目，传上去只会让 scope 匹配出莫名其妙的结果
                () -> assertEquals(null, query.commodityRef()),
                () -> assertEquals(null, query.categoryRef()));
    }

    @Test
    @DisplayName("下单：实付 = 面额 - 券抵扣，券在落单【之前】就锁上")
    void 下单抵扣并锁券() {
        RechargeResult result = service.create(cmd(new BigDecimal("100"), COUPON_ID));

        ArgumentCaptor<ExternalOrder> captor = ArgumentCaptor.forClass(ExternalOrder.class);
        verify(externalOrderDao).insert(captor.capture());
        ExternalOrder order = captor.getValue();
        assertAll(
                () -> assertTrue(result.accepted()),
                () -> assertEquals(0, new BigDecimal("90").compareTo(order.getPayAmount())),
                () -> assertEquals(0, new BigDecimal("100").compareTo(order.getOriginalAmount())),
                () -> assertEquals(0, new BigDecimal("10").compareTo(order.getCouponDiscount())),
                () -> assertEquals(ExternalOrderStatusEnum.UNPAID, order.getStatus()),
                // 手机号明文进来、落库时加密；列表要显示的是打码值，明文不存第二份
                () -> assertEquals(PHONE, order.getTargetAccount()),
                () -> assertEquals("138****8888", order.getTargetMasked()),
                () -> assertNotEquals(null, order.getExpireTime()));
        verify(couponWriteOffApi).lock(any());
    }

    @Test
    @DisplayName("🔴 抵扣额是服务端试算的，锁券时传的就是那个数")
    void 抵扣额由服务端算() {
        stubCouponUsable(new BigDecimal("30"));

        service.create(cmd(new BigDecimal("100"), COUPON_ID));

        ArgumentCaptor<CouponLockCmd> captor = ArgumentCaptor.forClass(CouponLockCmd.class);
        verify(couponWriteOffApi).lock(captor.capture());
        // 入参里根本没有「抵扣多少」这个字段 —— 让客户端报数就是可以直接刷钱的口子
        assertEquals(0, new BigDecimal("30").compareTo(captor.getValue().discountAmount()));
    }

    @Test
    @DisplayName("🔴 面额只认白名单 —— 任意金额是一条本来就不该存在的路")
    void 面额必须在白名单里() {
        assertAll(
                () -> assertEquals(RechargeReason.BAD_AMOUNT,
                        service.create(cmd(new BigDecimal("0.01"), null)).reason()),
                () -> assertEquals(RechargeReason.BAD_AMOUNT,
                        service.create(cmd(new BigDecimal("77"), null)).reason()),
                // 100 和 100.00 对用户是同一个面额，所以比的是 compareTo 不是 equals
                () -> assertTrue(service.create(cmd(new BigDecimal("100.00"), null)).accepted()));
    }

    @Test
    @DisplayName("手机号形状不对 → 拒绝，一个字都不写库")
    void 手机号要校验() {
        assertEquals(RechargeReason.BAD_TARGET,
                service.create(new RechargeCmd(MEMBER_ID, "1381234", new BigDecimal("100"), null)).reason());
        verify(externalOrderDao, never()).insert(any(ExternalOrder.class));
    }

    @Test
    @DisplayName("券不可用 → 拒绝，不会悄悄按原价下单")
    void 券不可用时拒绝() {
        when(couponQueryApi.trial(any()))
                .thenReturn(new CouponTrialView(List.of(), List.of()));

        // 悄悄按原价下单的话，用户会发现自己多花了钱而券还在
        assertEquals(RechargeReason.COUPON_UNUSABLE,
                service.create(cmd(new BigDecimal("100"), COUPON_ID)).reason());
        verify(externalOrderDao, never()).insert(any(ExternalOrder.class));
    }

    @Test
    @DisplayName("不用券也能下单，一次都不该碰券的接口")
    void 不用券() {
        assertTrue(service.create(cmd(new BigDecimal("100"), null)).accepted());

        verify(couponQueryApi, never()).trial(any());
        verify(couponWriteOffApi, never()).lock(any());
    }

    /* ---------------- 支付 + 执行 ---------------- */

    @Test
    @DisplayName("支付并执行：0 → 10 → 20 → 30，券在【付款】那一刻确认")
    void 支付并执行() {
        when(externalOrderDao.getByOrderNo(anyString())).thenReturn(unpaidOrder());
        when(externalOrderDao.markPaid(anyString())).thenReturn(1);
        when(externalOrderDao.markExecuting(anyString())).thenReturn(1);

        RechargeResult result = service.payAndExecute("E001", MEMBER_ID);

        assertTrue(result.accepted());
        verify(couponWriteOffApi).confirm(any());
        verify(externalOrderDao).markSuccess(anyString(), anyString());
        // 充完要告诉用户 —— 话费到没到账用户自己看不出来
        verify(notificationService).send(any());
    }

    @Test
    @DisplayName("🔴 抢不到执行闸门 → 不调运营商。重复调是给用户充了两次话费，没有回头路")
    void 抢不到执行闸门就不调运营商() {
        when(externalOrderDao.getByOrderNo(anyString())).thenReturn(unpaidOrder());
        when(externalOrderDao.markPaid(anyString())).thenReturn(1);
        when(externalOrderDao.markExecuting(anyString())).thenReturn(0);

        service.payAndExecute("E001", MEMBER_ID);

        verify(externalOrderDao, never()).markSuccess(anyString(), anyString());
    }

    @Test
    @DisplayName("🔴 抢不到支付闸门（超时 job 先到）→ 什么都不做")
    void 抢不到支付闸门() {
        when(externalOrderDao.getByOrderNo(anyString())).thenReturn(unpaidOrder());
        when(externalOrderDao.markPaid(anyString())).thenReturn(0);

        RechargeResult result = service.payAndExecute("E001", MEMBER_ID);

        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(RechargeReason.ORDER_NOT_PAYABLE, result.reason()),
                // 继续往下走会把一个已取消订单的券确认掉 —— 而那张券已经放回去了
                () -> verify(couponWriteOffApi, never()).confirm(any()),
                () -> verify(externalOrderDao, never()).markExecuting(anyString()));
    }

    @Test
    @DisplayName("🔴 不能支付别人的单子")
    void 不能支付别人的单() {
        when(externalOrderDao.getByOrderNo(anyString())).thenReturn(unpaidOrder());

        assertEquals(RechargeReason.ORDER_NOT_FOUND, service.payAndExecute("E001", 9999L).reason());
        verify(externalOrderDao, never()).markPaid(anyString());
    }

    /* ---------------- 取消 ---------------- */

    @Test
    @DisplayName("取消：券放回去，bizRefId 用的是单号本身")
    void 取消放回券() {
        when(externalOrderDao.markCancelled(anyString(), anyString())).thenReturn(1);

        assertTrue(service.cancel(unpaidOrder(), "超时未支付，自动取消"));
        verify(couponWriteOffApi).release(any());
    }

    @Test
    @DisplayName("🔴 抢不到取消闸门 → 整单放弃补偿，尤其不能把券放回去")
    void 抢不到取消闸门() {
        when(externalOrderDao.markCancelled(anyString(), anyString())).thenReturn(0);

        assertFalse(service.cancel(unpaidOrder(), "超时"));
        // 放了的话，一笔【刚支付成功】的单会把自己的券吐回用户券包里
        verify(couponWriteOffApi, never()).release(any());
    }

    /* ---------------- fixtures ---------------- */

    private void stubCouponUsable(BigDecimal discount) {
        CouponTrialView.Item item = new CouponTrialView.Item(
                COUPON_ID, "话费券", discount, true, null, null, null, "CASH");
        when(couponQueryApi.trial(any()))
                .thenReturn(new CouponTrialView(
                        List.of(new CouponTrialView.Group("CASH", List.of(item))), List.of()));
    }

    private static RechargeCmd cmd(BigDecimal amount, Long couponId) {
        return new RechargeCmd(MEMBER_ID, PHONE, amount, couponId);
    }

    private static ExternalOrder unpaidOrder() {
        ExternalOrder order = new ExternalOrder();
        order.setOrderNo("E001");
        order.setMemberId(MEMBER_ID);
        order.setSceneCode("MOBILE_RECHARGE");
        order.setTargetMasked("138****8888");
        order.setOriginalAmount(new BigDecimal("100"));
        order.setCouponId(COUPON_ID);
        order.setCouponDiscount(new BigDecimal("10"));
        order.setPayAmount(new BigDecimal("90"));
        order.setStatus(ExternalOrderStatusEnum.UNPAID);
        return order;
    }

    private ExternalRechargeService service(ExternalSceneProperties.Transport transport,
                                            SystemEnvironmentEnum env) {
        ExternalSceneProperties properties = new ExternalSceneProperties();
        properties.setTransport(transport);
        return new ExternalRechargeService(externalOrderDao, couponQueryApi, couponWriteOffApi,
                memberService, notificationService, properties,
                new SystemEnvironment(env == SystemEnvironmentEnum.PROD, "solvela", env));
    }
}
