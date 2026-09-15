package solvela.mall.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.enums.MallOrderStatusEnum;
import solvela.mall.MallOrder;
import solvela.mall.commodity.dao.MallCommodityDao;
import solvela.mall.exchangelimit.dao.MallExchangeLimitDao;
import solvela.mall.order.dao.MallOrderDao;
import solvela.mall.sku.dao.MallSkuDao;
import solvela.member.api.AssetDebitApi;
import solvela.member.api.AssetDebitCmd;
import solvela.member.api.AssetDebitResult;
import solvela.member.api.CouponWriteOffApi;
import solvela.member.api.CouponWriteOffCmd;
import solvela.member.api.CouponWriteOffView;
import solvela.notification.service.NotificationService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商城超时取消。
 *
 * <h3>这里守的是一条对称性：用户付出的每一样东西都要还回去</h3>
 * 一单里用户付出的是<b>积分 + 一张券</b>。只退积分不放券，券就白扣了 ——
 * 而且不报错，只有用户会发现券包里少了一张。
 *
 * <h3>🔴 而退这两样东西的 bizRefId 规则<b>刚好相反</b></h3>
 * <ul>
 *   <li>退积分要用 <b>不同</b>的 bizRefId（加 {@code :REFUND} 后缀）——
 *       否则被 {@code uk(biz_ref_id, asset_type)} 当成重复提交挡掉，
 *       表现是「退款静默不生效」；</li>
 *   <li>放券要用 <b>一样</b>的 bizRefId（订单号本身）——
 *       条件更新是 {@code WHERE locked_biz_id = ?}，
 *       传了别的单号什么都不会发生，同样不报错。</li>
 * </ul>
 * 两条规则相反且都静默失败，所以它们各有一条用例钉着。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MallOrderCancelServiceTest {

    private static final String ORDER_NO = "M20260915120000123ABC";
    private static final Long MEMBER_ID = 1001L;
    private static final Long COUPON_ID = 777L;

    @Mock
    private MallOrderDao mallOrderDao;
    @Mock
    private MallSkuDao mallSkuDao;
    @Mock
    private MallExchangeLimitDao mallExchangeLimitDao;
    @Mock
    private MallCommodityDao mallCommodityDao;
    @Mock
    private AssetDebitApi assetDebitApi;
    @Mock
    private CouponWriteOffApi couponWriteOffApi;
    @Mock
    private NotificationService notificationService;

    private MallOrderCancelService service;

    @BeforeEach
    void setUp() {
        service = new MallOrderCancelService(mallOrderDao, mallSkuDao, mallExchangeLimitDao,
                mallCommodityDao, assetDebitApi, couponWriteOffApi, notificationService);
        when(mallOrderDao.markCancelled(anyString(), anyString())).thenReturn(1);
        when(assetDebitApi.refund(any())).thenReturn(AssetDebitResult.ofAccepted());
        when(couponWriteOffApi.release(any()))
                .thenReturn(new CouponWriteOffView(true, false, null, null));
    }

    @Test
    @DisplayName("🔴 取消时积分和券一起还回去 —— 只退一半等于把券白扣了")
    void 积分和券一起退() {
        assertTrue(service.cancelExpired(orderWithCoupon()));

        verify(assetDebitApi).refund(any());
        verify(couponWriteOffApi).release(any());
    }

    @Test
    @DisplayName("🔴 放券的 bizRefId 是订单号本身，不加 :REFUND 后缀 —— 和退积分刚好相反")
    void 放券用订单号退积分用后缀() {
        service.cancelExpired(orderWithCoupon());

        ArgumentCaptor<AssetDebitCmd> refund = ArgumentCaptor.forClass(AssetDebitCmd.class);
        verify(assetDebitApi).refund(refund.capture());
        ArgumentCaptor<CouponWriteOffCmd> release = ArgumentCaptor.forClass(CouponWriteOffCmd.class);
        verify(couponWriteOffApi).release(release.capture());

        assertAll(
                // 退积分：必须和扣款时【不同】，否则被唯一键当重复提交挡掉，退款静默不生效
                () -> assertNotEquals(ORDER_NO, refund.getValue().bizRefId()),
                () -> assertTrue(refund.getValue().bizRefId().startsWith(ORDER_NO)),
                // 放券：必须和锁定时【一样】，否则 WHERE locked_biz_id = ? 匹配不上，
                // 券永远留在「锁定中」，同样不报错
                () -> assertEquals(ORDER_NO, release.getValue().bizRefId()));
    }

    @Test
    @DisplayName("没用券的单子一次都不该碰券的接口")
    void 没用券就不碰券接口() {
        MallOrder order = orderWithCoupon();
        order.setCouponId(null);

        service.cancelExpired(order);

        verify(couponWriteOffApi, never()).release(any());
    }

    @Test
    @DisplayName("⚠️ 券放不回去只告警，不回滚 —— 否则用户会看到「订单还在但积分已经回来了」")
    void 放券失败不影响取消() {
        when(couponWriteOffApi.release(any()))
                .thenReturn(new CouponWriteOffView(false, false, null, "券不在本单的锁定中状态"));

        // 到这一步订单已经取消、积分已经退了。为一张券把整个取消事务回滚会更糟，
        // 而且兜底任务本来就会在锁定超时后接手 —— 真实后果只是「晚一点回来」
        assertTrue(service.cancelExpired(orderWithCoupon()));
        verify(assetDebitApi).refund(any());
    }

    @Test
    @DisplayName("抢不到取消闸门（多半是刚支付成功）→ 什么都不做，尤其不能把券放回去")
    void 抢不到闸门就整单放弃() {
        when(mallOrderDao.markCancelled(anyString(), anyString())).thenReturn(0);

        assertFalse(service.cancelExpired(orderWithCoupon()));

        // 放了的话，一笔【刚支付成功】的订单会把自己的券吐回用户券包里
        verify(couponWriteOffApi, never()).release(any());
        verify(assetDebitApi, never()).refund(any());
    }

    private static MallOrder orderWithCoupon() {
        MallOrder order = new MallOrder();
        order.setOrderNo(ORDER_NO);
        order.setMemberId(MEMBER_ID);
        order.setCommodityName("测试商品");
        order.setSkuId(1L);
        order.setQuantity(1);
        order.setPayPoints(4000);
        order.setStatus(MallOrderStatusEnum.UNPAID);
        order.setCouponId(COUPON_ID);
        order.setCouponDiscount(new BigDecimal("1000"));
        return order;
    }
}
