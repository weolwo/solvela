package solvela.mall.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import solvela.enums.EnableStatusEnum;
import solvela.enums.MallCommodityStatusEnum;
import solvela.enums.MallOrderStatusEnum;
import solvela.enums.MallPayTypeEnum;
import solvela.mall.order.event.MallOrderActionPublisher;
import solvela.mall.MallAddress;
import solvela.mall.MallCommodity;
import solvela.mall.MallOrder;
import solvela.mall.MallSku;
import solvela.mall.address.service.MallAddressService;
import solvela.mall.commodity.manager.MallCommodityManager;
import solvela.mall.exchangelimit.dao.MallExchangeLimitDao;
import solvela.mall.order.event.MallOrderPendingEvent;
import solvela.mall.order.manager.MallOrderManager;
import solvela.mall.sku.dao.MallSkuDao;
import solvela.mall.sku.manager.MallSkuManager;
import solvela.marketing.api.MallRedeemCmd;
import solvela.marketing.api.MallRedeemReason;
import solvela.marketing.api.MallRedeemResult;
import solvela.member.api.AssetDebitApi;
import solvela.member.api.CouponLockCmd;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.CouponTrialQuery;
import solvela.member.api.CouponTrialView;
import solvela.member.api.CouponWriteOffApi;
import solvela.member.api.CouponWriteOffCmd;
import solvela.member.api.CouponWriteOffView;
import solvela.member.api.AssetDebitCmd;
import solvela.member.api.AssetDebitReason;
import solvela.member.api.AssetDebitResult;
import solvela.member.service.MemberService;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商城兑换：<b>钱和货同时动的那一个事务</b>。
 *
 * <h3>为什么这个方法值得这么多用例</h3>
 * {@code redeem} 一个事务里连着做四件事 —— 占库存 → 占限兑 → 扣积分 → 落订单，
 * 每一步都可能失败，而失败的<b>善后方式不一样</b>：第一步之前失败什么都不用管，
 * 第一步之后失败必须让事务回滚。这个区别在代码里只差一个方法名：
 *
 * <pre>
 *   return MallRedeemResult.ofReject(...)   // 还没写库 —— 普通返回
 *   return reject(...)                      // 已占库存 —— 内部会 markRollbackOnly()
 * </pre>
 *
 * 写错的后果不会报错，也不会被日志记下：<b>库存扣了、限兑占了、订单没落，
 * 用户什么都没拿到</b>，而库里那件商品从此少一件。本类的核心就是把
 * 「①之后的每一次拒绝都标了回滚」逐条钉死，让下一个往中间插分支的人一改就红。
 *
 * <h3>顺序也是一道防线</h3>
 * <b>先占资源、最后扣钱。</b>反过来的话，扣完积分才发现没库存 ——
 * 事务能回滚，但只要有谁给某一步加了 {@code REQUIRES_NEW}，
 * 表现就是「钱扣了、单没有、重试还说重复提交」。所以顺序单独有一条用例。
 *
 * <h3>关于 markRollbackOnly 被 spy 掉</h3>
 * 生产代码里它是 {@code TransactionAspectSupport.currentTransactionStatus()}，
 * 读 Spring 内部的 ThreadLocal；单测没有活动事务，真调会抛 {@code NoTransactionException}。
 * 这里 spy 成空实现，于是它从「测不了的副作用」变成「可断言的一次调用」。
 *
 * @Author alaric
 * @Date 2026-09-06
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MallRedeemServiceTest {

    private static final Long MEMBER_ID = 900001L;
    private static final Long SKU_ID = 51L;
    private static final Long COMMODITY_ID = 7L;
    private static final Long ADDRESS_ID = 33L;

    @Mock
    private MallCommodityManager mallCommodityManager;
    @Mock
    private MallSkuManager mallSkuManager;
    @Mock
    private MallSkuDao mallSkuDao;
    @Mock
    private MallExchangeLimitDao mallExchangeLimitDao;
    @Mock
    private MallOrderManager mallOrderManager;
    @Mock
    private MallAddressService mallAddressService;
    @Mock
    private MemberService memberService;
    @Mock
    private AssetDebitApi assetDebitApi;
    @Mock
    private CouponQueryApi couponQueryApi;
    @Mock
    private CouponWriteOffApi couponWriteOffApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    /**
     * 打点：这一单付掉了。
     *
     * <p>🔴 <b>纯积分单必须发、待支付的混合单必须不发</b> —— 见
     * {@code 纯积分单落单即打点} / {@code 待支付的混合单不打点} 两个用例。
     * 那是 ORDER_PAID 两个产生点里最容易漏的一半：纯积分单不经过
     * {@code MallPayService}，落单那一刻资产就已经结清了。
     */
    @Mock
    private MallOrderActionPublisher orderActionPublisher;

    private MallRedeemService service;

    private static final Long COUPON_ID = 777L;

    private MallCommodity commodity;
    private MallSku sku;

    @BeforeEach
    void setUp() {
        service = spy(new MallRedeemService(mallCommodityManager, mallSkuManager, mallSkuDao,
                mallExchangeLimitDao, mallOrderManager, mallAddressService, memberService,
                assetDebitApi, couponQueryApi, couponWriteOffApi, eventPublisher,
                orderActionPublisher));
        // 没有活动事务，真调会抛 NoTransactionException；spy 成空实现后它变成一次可断言的调用
        doNothing().when(service).markRollbackOnly();

        commodity = new MallCommodity();
        commodity.setId(COMMODITY_ID);
        commodity.setCommodityCode("CMD001");
        commodity.setCommodityName("联名T恤");
        commodity.setCommodityType("VIRTUAL");
        commodity.setStatus(MallCommodityStatusEnum.ON);
        commodity.setPayType(MallPayTypeEnum.POINTS);
        commodity.setPointsPrice(5000);
        commodity.setCashPrice(BigDecimal.ZERO);

        sku = new MallSku();
        sku.setId(SKU_ID);
        sku.setCommodityId(COMMODITY_ID);
        sku.setSkuCode("SKU001");
        sku.setSkuAttrs("{\"尺码\":\"L\"}");
        sku.setSkuStatus(EnableStatusEnum.ENABLED);

        when(mallSkuManager.getById(SKU_ID)).thenReturn(sku);
        when(mallCommodityManager.getById(COMMODITY_ID)).thenReturn(commodity);
        when(mallSkuDao.sell(anyLong(), anyInt())).thenReturn(1);
        when(mallSkuDao.lock(anyLong(), anyInt())).thenReturn(1);
        when(mallExchangeLimitDao.tryConsume(anyLong(), anyLong(), any(), anyInt(), anyInt())).thenReturn(1);
        when(assetDebitApi.debit(any())).thenReturn(AssetDebitResult.ofAccepted());
        when(memberService.requireMemberName(MEMBER_ID)).thenReturn("sv900001");
    }

    // ------------------------------------------------------------------ 正常路径

    @Test
    @DisplayName("纯积分兑换：占库存 → 扣积分 → 落待履约单 → 发履约事件")
    void 纯积分兑换走完全程() {
        MallRedeemResult result = service.redeem(cmd(1));

        assertTrue(result.accepted());
        assertEquals(MallOrderStatusEnum.PENDING, result.status());

        verify(mallSkuDao).sell(SKU_ID, 1);
        // 纯积分是同步扣的，不存在悬挂，所以不该走锁库存那条路
        verify(mallSkuDao, never()).lock(anyLong(), anyInt());

        MallOrder order = savedOrder();
        assertEquals(MallOrderStatusEnum.PENDING, order.getStatus());
        assertEquals(result.orderNo(), order.getOrderNo());
        verify(eventPublisher).publishEvent(new MallOrderPendingEvent(order.getOrderNo()));
        verify(service, never()).markRollbackOnly();
    }

    @Test
    @DisplayName("🔴 纯积分单落单即打点：它不经过支付，ORDER_PAID 只能在这里产生")
    void 纯积分单落单即打点() {
        service.redeem(cmd(1));

        /*
         * 为什么这条断言值得单独存在：
         *
         * ORDER_PAID 有【两个】产生点 —— 混合单在 MallPayService.pay，
         * 纯积分单在这里。只埋前者是一个非常容易犯、而且【完全不报错】的错误：
         * 纯积分是这个平台的主要兑换方式，漏掉它的表现是
         * 「订单类任务基本不动」，只会以客诉的形式出现。
         *
         * 优惠券方案 §11.8 记着，阶段 4 少的正是同一个岔路口的同一半。
         */
        // ⚠️ savedOrder() 内部自己有一次 verify，不能写成 verify(x).f(savedOrder())：
        //    verify 的参数里再套一个 verify，Mockito 会报 UnfinishedVerificationException
        MallOrder order = savedOrder();
        verify(orderActionPublisher).publishOrderPaid(order);
    }

    @Test
    @DisplayName("🔴 顺序不能换：先占库存、再占限兑、最后才扣钱")
    void 先占资源最后扣钱() {
        commodity.setLimitCount(3);

        service.redeem(cmd(1));

        // 反过来的话，扣完积分才发现没库存 —— 事务能回滚，但只要有谁给某一步加了
        // REQUIRES_NEW，表现就是「钱扣了、单没有、重试还说重复提交」
        InOrder order = inOrder(mallSkuDao, mallExchangeLimitDao, assetDebitApi, mallOrderManager);
        order.verify(mallSkuDao).sell(anyLong(), anyInt());
        order.verify(mallExchangeLimitDao).tryConsume(anyLong(), anyLong(), any(), anyInt(), anyInt());
        order.verify(assetDebitApi).debit(any());
        order.verify(mallOrderManager).save(any(MallOrder.class));
    }

    @Test
    @DisplayName("🔴 订单号就是扣积分的幂等键：两边必须是同一个串")
    void 订单号即幂等键() {
        MallRedeemResult result = service.redeem(cmd(1));

        ArgumentCaptor<AssetDebitCmd> captor = ArgumentCaptor.forClass(AssetDebitCmd.class);
        verify(assetDebitApi).debit(captor.capture());
        AssetDebitCmd debit = captor.getValue();

        // t_member_asset_transaction 上 uk(biz_ref_id, asset_type) 就是靠这个去重的。
        // 两边不是同一个串，重复扣款就没有任何东西拦得住
        assertEquals(result.orderNo(), debit.bizRefId(), "扣款的 bizRefId 必须等于订单号");
        assertEquals(savedOrder().getOrderNo(), debit.bizRefId());
        assertEquals("SCORE", debit.assetType());
    }

    @Test
    @DisplayName("积分+现金：走锁库存、落待支付，且不发履约事件")
    void 积分加现金挂在待支付() {
        commodity.setPayType(MallPayTypeEnum.POINTS_CASH);
        commodity.setCashPrice(new BigDecimal("9.90"));

        MallRedeemResult result = service.redeem(cmd(2));

        assertEquals(MallOrderStatusEnum.UNPAID, result.status());
        verify(mallSkuDao).lock(SKU_ID, 2);
        verify(mallSkuDao, never()).sell(anyLong(), anyInt());

        MallOrder order = savedOrder();
        assertEquals(MallOrderStatusEnum.UNPAID, order.getStatus());
        // 钱还没收就发货等于白送。这条路要等支付回调把它推到 10
        verify(eventPublisher, never()).publishEvent(any(MallOrderPendingEvent.class));
        assertEquals(0, new BigDecimal("19.80").compareTo(order.getPayCash()), "现金部分要乘数量");

        /*
         * 🔴 也不能打点：钱还没收，这一单还不算「付掉了」。
         *
         * 这里发了的话，用户只要把商品加进购物车式地下个单、然后【不付钱】，
         * 就能刷满「下单 N 次」的任务 —— 而超时 job 随后会把单取消，
         * 进度却已经涨上去了，且不会退。
         *
         * 判据和履约刻意是同一个（status == PENDING），所以两条断言并排放：
         * 将来有谁改了那个判据，会同时看到两个后果。
         */
        verify(orderActionPublisher, never()).publishOrderPaid(any());
    }

    @Test
    @DisplayName("免费商品不去扣积分：0 是合法价格，不是「没设置」")
    void 零积分不调扣款() {
        commodity.setPointsPrice(0);

        MallRedeemResult result = service.redeem(cmd(1));

        assertTrue(result.accepted());
        verify(assetDebitApi, never()).debit(any());
        assertEquals(0, savedOrder().getPayPoints());
    }

    @Test
    @DisplayName("SKU 价为 null 时继承商品基准价（null 才是「没设置」，0 是免费）")
    void SKU价继承商品价() {
        sku.setSkuPointsPrice(null);
        service.redeem(cmd(2));
        assertEquals(5000 * 2, debitAmount().intValue());

        reset();
        sku.setSkuPointsPrice(300);
        service.redeem(cmd(2));
        assertEquals(300 * 2, debitAmount().intValue());
    }

    @Test
    @DisplayName("数量夹在 1..20：null/0 当 1，超上限截到 20")
    void 数量夹取() {
        service.redeem(MallRedeemCmd.withoutCoupon(MEMBER_ID, SKU_ID, null, null));
        verify(mallSkuDao).sell(SKU_ID, 1);

        reset();
        service.redeem(cmd(0));
        verify(mallSkuDao).sell(SKU_ID, 1);

        reset();
        // 不封的话一个 quantity=99999 会把库存条件判断变成一次巨额扣减
        service.redeem(cmd(99999));
        verify(mallSkuDao).sell(SKU_ID, 20);
    }

    @Test
    @DisplayName("订单存的是商品快照，不是外键：改名改价不该动历史订单")
    void 订单存快照() {
        sku.setSkuPointsPrice(4200);
        service.redeem(cmd(1));

        MallOrder order = savedOrder();
        assertAll(
                () -> assertEquals("联名T恤", order.getCommodityName()),
                () -> assertEquals("CMD001", order.getCommodityCode()),
                () -> assertEquals("SKU001", order.getSkuCode()),
                () -> assertEquals("{\"尺码\":\"L\"}", order.getSkuAttrs()),
                () -> assertEquals(4200, order.getPointsPrice()),
                // 下单当时那个账号，不是这人现在叫什么 —— 审计要回答的是「当时是谁」
                () -> assertEquals("sv900001", order.getMemberName()));
    }

    // ------------------------------------------------------------------ ① 之前的拒绝：不该标回滚

    @Test
    @DisplayName("SKU 不存在：当场拒绝，一个字都不写")
    void SKU不存在() {
        when(mallSkuManager.getById(SKU_ID)).thenReturn(null);

        assertRejectedWithoutTouchingAnything(service.redeem(cmd(1)), MallRedeemReason.SKU_NOT_FOUND);
    }

    @Test
    @DisplayName("商品已下架：与不存在给同一个原因，对用户都是「兑不了」")
    void 商品下架() {
        commodity.setStatus(MallCommodityStatusEnum.OFF);

        assertRejectedWithoutTouchingAnything(service.redeem(cmd(1)), MallRedeemReason.COMMODITY_OFF);
    }

    @Test
    @DisplayName("不在上架时间窗内：还没开始 / 已经结束都拒绝")
    void 不在上架时间窗() {
        commodity.setStartTime(LocalDateTime.now().plusDays(1));
        assertRejectedWithoutTouchingAnything(service.redeem(cmd(1)), MallRedeemReason.COMMODITY_OFF);

        reset();
        commodity.setStartTime(null);
        commodity.setEndTime(LocalDateTime.now().minusDays(1));
        assertRejectedWithoutTouchingAnything(service.redeem(cmd(1)), MallRedeemReason.COMMODITY_OFF);
    }

    @Test
    @DisplayName("实物没选地址：拒绝，不占库存")
    void 实物必须有地址() {
        commodity.setCommodityType("PHYSICAL");

        assertRejectedWithoutTouchingAnything(
                service.redeem(MallRedeemCmd.withoutCoupon(MEMBER_ID, SKU_ID, 1, null)),
                MallRedeemReason.ADDRESS_REQUIRED);
    }

    @Test
    @DisplayName("🔴 地址不是自己的（或支付期间被删了）：重查拿不到就拦下，不拿空地址去建履约单")
    void 地址查不到就拦住() {
        commodity.setCommodityType("PHYSICAL");
        when(mallAddressService.getOwned(ADDRESS_ID, MEMBER_ID)).thenReturn(null);

        assertRejectedWithoutTouchingAnything(service.redeem(cmd(1)), MallRedeemReason.ADDRESS_NOT_FOUND);
    }

    @Test
    @DisplayName("库存扣不动：这是①本身失败，什么都没写，不必回滚")
    void 库存不足() {
        when(mallSkuDao.sell(anyLong(), anyInt())).thenReturn(0);

        MallRedeemResult result = service.redeem(cmd(1));

        assertFalse(result.accepted());
        assertEquals(MallRedeemReason.OUT_OF_STOCK, result.reason());
        verify(mallExchangeLimitDao, never()).tryConsume(anyLong(), anyLong(), any(), anyInt(), anyInt());
        verify(assetDebitApi, never()).debit(any());
        verify(mallOrderManager, never()).save(any(MallOrder.class));
        // 条件 UPDATE 影响 0 行 = 没扣成，没有任何东西需要撤销
        verify(service, never()).markRollbackOnly();
    }

    // ------------------------------------------------------------------ ① 之后的拒绝：必须标回滚

    @Test
    @DisplayName("🔴 限兑用尽：必须标回滚，否则库存白扣一件")
    void 限兑用尽要回滚() {
        commodity.setLimitCount(3);
        when(mallExchangeLimitDao.tryConsume(anyLong(), anyLong(), any(), anyInt(), anyInt())).thenReturn(0);

        MallRedeemResult result = service.redeem(cmd(1));

        assertEquals(MallRedeemReason.EXCHANGE_LIMITED, result.reason());
        assertRolledBackAndNothingCommitted();
    }

    @Test
    @DisplayName("🔴 积分不足：必须标回滚，否则库存和限兑都白占")
    void 积分不足要回滚() {
        when(assetDebitApi.debit(any()))
                .thenReturn(AssetDebitResult.ofReject(AssetDebitReason.BALANCE_NOT_ENOUGH));

        MallRedeemResult result = service.redeem(cmd(1));

        assertEquals(MallRedeemReason.POINTS_NOT_ENOUGH, result.reason());
        assertRolledBackAndNothingCommitted();
    }

    @Test
    @DisplayName("🔴 扣款的每一种失败都要落到用户看得懂的原因，且都要回滚")
    void 扣款失败原因映射() {
        assertDebitFailureMapsTo(AssetDebitReason.BALANCE_NOT_ENOUGH, MallRedeemReason.POINTS_NOT_ENOUGH);
        assertDebitFailureMapsTo(AssetDebitReason.WALLET_UNAVAILABLE, MallRedeemReason.WALLET_UNAVAILABLE);
        assertDebitFailureMapsTo(AssetDebitReason.CONCURRENT_CONFLICT, MallRedeemReason.CONCURRENT_CONFLICT);
        // 会员不存在意味着调用方拿了个假 id —— 对用户是「服务出问题了」，不是他的错
        assertDebitFailureMapsTo(AssetDebitReason.MEMBER_NOT_FOUND, MallRedeemReason.INTERNAL);
        assertDebitFailureMapsTo(AssetDebitReason.UNKNOWN, MallRedeemReason.INTERNAL);
    }

    @Test
    @DisplayName("没配限兑（limitCount 为空或 0）就不去占额度")
    void 没配限兑不占额度() {
        commodity.setLimitCount(null);
        service.redeem(cmd(1));
        verify(mallExchangeLimitDao, never()).tryConsume(anyLong(), anyLong(), any(), anyInt(), anyInt());

        reset();
        commodity.setLimitCount(0);
        service.redeem(cmd(1));
        verify(mallExchangeLimitDao, never()).tryConsume(anyLong(), anyLong(), any(), anyInt(), anyInt());
    }

    // ------------------------------------------------------------------ 断言辅助

    /** ①之前的拒绝：库存、限兑、扣款、落单一个都不该发生，也不必回滚 */
    private void assertRejectedWithoutTouchingAnything(MallRedeemResult result, MallRedeemReason expected) {
        assertFalse(result.accepted());
        assertEquals(expected, result.reason());
        assertNull(result.orderNo());
        verify(mallSkuDao, never()).sell(anyLong(), anyInt());
        verify(mallSkuDao, never()).lock(anyLong(), anyInt());
        verify(assetDebitApi, never()).debit(any());
        verify(mallOrderManager, never()).save(any(MallOrder.class));
        verify(service, never()).markRollbackOnly();
    }

    /** ①之后的拒绝：必须标了回滚，且订单与履约事件都不能产生 */
    private void assertRolledBackAndNothingCommitted() {
        verify(service).markRollbackOnly();
        verify(mallOrderManager, never()).save(any(MallOrder.class));
        verify(eventPublisher, never()).publishEvent(any(MallOrderPendingEvent.class));
    }

    private void assertDebitFailureMapsTo(AssetDebitReason from, MallRedeemReason to) {
        reset();
        when(assetDebitApi.debit(any())).thenReturn(AssetDebitResult.ofReject(from));

        MallRedeemResult result = service.redeem(cmd(1));

        assertEquals(to, result.reason(), from + " 应当映射成 " + to);
        assertRolledBackAndNothingCommitted();
    }

    // ------------------------------------------------------------------ 用券（阶段 4）

    @Test
    @DisplayName("🔴 用券：实付 = 原价 - 抵扣，券在扣款【之前】锁上，落单后确认")
    void 用券抵扣积分() {
        stubCouponUsable(1000);

        MallRedeemResult result = service.redeem(cmdWithCoupon(COUPON_ID));

        assertTrue(result.accepted());
        MallOrder order = savedOrder();
        assertAll(
                // 5000 分的商品，券减 1000 → 实付 4000
                () -> assertEquals(4000, order.getPayPoints()),
                () -> assertEquals(COUPON_ID, order.getCouponId()),
                () -> assertEquals(0, new BigDecimal("1000").compareTo(order.getCouponDiscount())),
                // 扣的是抵扣后的钱。扣原价的话券等于白用了，而且不报错
                () -> assertEquals(0, new BigDecimal("4000").compareTo(debitAmount())));

        // 锁券必须在扣款之前：它和库存、限购是同一类「已占资源」，失败要一起回滚
        InOrder ordered = inOrder(couponWriteOffApi, assetDebitApi, mallOrderManager);
        ordered.verify(couponWriteOffApi).lock(any());
        ordered.verify(assetDebitApi).debit(any());
        ordered.verify(mallOrderManager).save(any(MallOrder.class));
    }

    @Test
    @DisplayName("🔴 抵扣额是服务端重新试算的，客户端连这个字段都没有")
    void 抵扣额由服务端算() {
        stubCouponUsable(1000);

        service.redeem(cmdWithCoupon(COUPON_ID));

        ArgumentCaptor<CouponLockCmd> captor = ArgumentCaptor.forClass(CouponLockCmd.class);
        verify(couponWriteOffApi).lock(captor.capture());
        // 让客户端报「减多少」就是一个可以直接刷钱的口子，而且不会有任何报错
        assertEquals(0, new BigDecimal("1000").compareTo(captor.getValue().discountAmount()));
        // 抵扣前金额也要落进流水，否则退款不知道退多少、财务对不了账
        assertEquals(0, new BigDecimal("5000").compareTo(captor.getValue().payAmount()));
    }

    @Test
    @DisplayName("🔴 订单落成待履约 = 积分已经扣了没有回头路，这一刻就确认券")
    void 落单后立刻确认券() {
        stubCouponUsable(1000);

        MallRedeemResult result = service.redeem(cmdWithCoupon(COUPON_ID));

        ArgumentCaptor<CouponWriteOffCmd> captor = ArgumentCaptor.forClass(CouponWriteOffCmd.class);
        verify(couponWriteOffApi).confirm(captor.capture());
        /*
         * 不确认的话券会一直挂在「锁定中」，兜底任务 120 分钟后把它放回去 ——
         * 于是变成「积分扣了、折扣享了、券还在」。
         * bizRefId 必须是订单号：条件更新是 WHERE locked_biz_id = ?
         */
        assertEquals(result.orderNo(), captor.getValue().bizRefId());
    }

    @Test
    @DisplayName("券在试算里不可用 → 拒绝并回滚，不会悄悄按原价下单")
    void 券不可用时拒绝并回滚() {
        when(couponQueryApi.trial(any()))
                .thenReturn(new CouponTrialView(List.of(), List.of()));

        MallRedeemResult result = service.redeem(cmdWithCoupon(COUPON_ID));

        // 悄悄按原价下单的话，用户会发现自己多花了积分而券还在 —— 那是投诉
        assertEquals(MallRedeemReason.COUPON_UNUSABLE, result.reason());
        assertRolledBackAndNothingCommitted();
        verify(couponWriteOffApi, never()).lock(any());
    }

    @Test
    @DisplayName("🔴 试算到锁定之间被别的单抢走了 → 拒绝并回滚")
    void 券锁不上时拒绝并回滚() {
        stubCouponUsable(1000);
        when(couponWriteOffApi.lock(any()))
                .thenReturn(new CouponWriteOffView(false, false, null, "券已被使用或不可用"));

        MallRedeemResult result = service.redeem(cmdWithCoupon(COUPON_ID));

        assertEquals(MallRedeemReason.COUPON_UNUSABLE, result.reason());
        // 锁不上却继续扣抵扣后的钱，就是平台白送了 1000 分
        assertRolledBackAndNothingCommitted();
        verify(assetDebitApi, never()).debit(any());
    }

    @Test
    @DisplayName("积分+现金单用现金券：减现金，积分一分没动")
    void 混合支付单用现金券抵现金() {
        commodity.setPayType(MallPayTypeEnum.POINTS_CASH);
        commodity.setCashPrice(new BigDecimal("50.00"));
        stubCouponUsable(10, "CASH");

        MallRedeemResult result = service.redeem(cmdWithCoupon(COUPON_ID));

        MallOrder order = savedOrder();
        assertAll(
                () -> assertTrue(result.accepted()),
                () -> assertEquals(0, new BigDecimal("40.00").compareTo(order.getPayCash())),
                () -> assertEquals(5000, order.getPayPoints()),
                () -> assertEquals(0, new BigDecimal("10").compareTo(order.getCouponDiscount())));

        // 混合单落的是待支付，券要等【付款】那一刻才确认 —— 这里不该确认
        verify(couponWriteOffApi, never()).confirm(any());
    }

    @Test
    @DisplayName("🔴 积分+现金单用积分券：减【积分】—— 曾经这张券在混合单上根本用不了")
    void 混合支付单用积分券抵积分() {
        commodity.setPayType(MallPayTypeEnum.POINTS_CASH);
        commodity.setCashPrice(new BigDecimal("50.00"));
        stubCouponUsable(1000, "SCORE");

        MallRedeemResult result = service.redeem(cmdWithCoupon(COUPON_ID));

        ArgumentCaptor<CouponTrialQuery> trialCaptor = ArgumentCaptor.forClass(CouponTrialQuery.class);
        verify(couponQueryApi).trial(trialCaptor.capture());
        MallOrder order = savedOrder();
        assertAll(
                () -> assertTrue(result.accepted()),
                /*
                 * 🔴 两个应付都要传上去。此前这里只传【一个】应付 + 一个抵扣对象，
                 *    混合单被硬性定成「只抵现金」，于是积分券直接不可用 ——
                 *    用户手里的积分券在混合单上凭空消失。
                 */
                () -> assertEquals(5000, trialCaptor.getValue().payPoints().intValue()),
                () -> assertEquals(0, new BigDecimal("50.00").compareTo(trialCaptor.getValue().payCash())),
                // 积分被减掉，现金【一分没动】
                () -> assertEquals(4000, order.getPayPoints()),
                () -> assertEquals(0, new BigDecimal("50.00").compareTo(order.getPayCash())));

        verify(couponWriteOffApi, never()).confirm(any());
    }

    @Test
    @DisplayName("不用券时一次都不该碰券的接口")
    void 不用券就不碰券接口() {
        service.redeem(cmd(1));

        verifyNoInteractions(couponQueryApi, couponWriteOffApi);
        assertNull(savedOrder().getCouponId());
    }

    /** 让试算返回一张能减 {@code discount} 分的<b>积分</b>券，并让锁定成功 */
    private void stubCouponUsable(int discount) {
        stubCouponUsable(discount, "SCORE");
    }

    /** 让试算返回一张券，{@code deductTarget} 决定它抵积分还是抵现金 */
    private void stubCouponUsable(int discount, String deductTarget) {
        CouponTrialView.Item item = new CouponTrialView.Item(
                COUPON_ID, "测试券", BigDecimal.valueOf(discount), true, null, null, null, deductTarget);
        when(couponQueryApi.trial(any()))
                .thenReturn(new CouponTrialView(
                        List.of(new CouponTrialView.Group(deductTarget, List.of(item))), List.of()));
        when(couponWriteOffApi.lock(any()))
                .thenReturn(new CouponWriteOffView(true, false, BigDecimal.valueOf(discount), null));
        when(couponWriteOffApi.confirm(any()))
                .thenReturn(new CouponWriteOffView(true, false, BigDecimal.valueOf(discount), null));
    }

    private MallRedeemCmd cmd(int quantity) {
        return MallRedeemCmd.withoutCoupon(MEMBER_ID, SKU_ID, quantity, ADDRESS_ID);
    }

    private MallRedeemCmd cmdWithCoupon(Long couponId) {
        return new MallRedeemCmd(MEMBER_ID, SKU_ID, 1, ADDRESS_ID, couponId);
    }

    private MallOrder savedOrder() {
        ArgumentCaptor<MallOrder> captor = ArgumentCaptor.forClass(MallOrder.class);
        verify(mallOrderManager).save(captor.capture());
        return captor.getValue();
    }

    private BigDecimal debitAmount() {
        ArgumentCaptor<AssetDebitCmd> captor = ArgumentCaptor.forClass(AssetDebitCmd.class);
        verify(assetDebitApi).debit(captor.capture());
        return captor.getValue().amount();
    }

    /** 同一条用例里跑第二遍时清掉调用记录，避免 verify 撞上一轮的调用 */
    private void reset() {
        org.mockito.Mockito.clearInvocations(service, mallSkuDao, mallExchangeLimitDao,
                assetDebitApi, couponQueryApi, couponWriteOffApi, mallOrderManager, eventPublisher);
    }
}
