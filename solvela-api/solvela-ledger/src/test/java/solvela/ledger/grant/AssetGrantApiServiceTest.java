package solvela.ledger.grant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import solvela.code.BizErrorCode;
import solvela.enums.PrizeTypeEnum;
import solvela.exception.BusinessException;
import solvela.ledger.MemberCoupon;
import solvela.ledger.PhysicalDelivery;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;
import solvela.ledger.wallet.service.MemberWalletService;
import solvela.member.api.AssetGrantCmd;
import solvela.member.api.AssetGrantReason;
import solvela.member.api.AssetGrantResult;
import solvela.member.service.MemberService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资产发放：商城履约真正把东西交到用户手里的那一步。
 *
 * <h3>这里守的两条，都是「钱货两清」的边界</h3>
 * ① <b>拒绝和故障不能混。</b>返回拒绝 = 重试也没用，调用方该把单子标死等人来看；
 *    抛异常 = 可以再试，调用方该回滚。混成一种，要么永远重试一个死单，
 *    要么一次数据库抖动让用户的东西彻底发不出来。
 * ② <b>券是实例类资产，quantity 份就是 quantity 行。</b>
 *    不能像余额那样把份数乘进金额里 —— 那样用户兑 3 张只会拿到 1 张。
 *
 * @Date 2026-09-05
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetGrantApiServiceTest {

    private static final Long MEMBER_ID = 1001L;
    private static final String ORDER_NO = "M20260905120000123ABCDEF";

    @Mock
    private PhysicalDeliveryDao physicalDeliveryDao;
    @Mock
    private MemberCouponDao memberCouponDao;
    @Mock
    private MemberWalletService memberWalletService;
    @Mock
    private MemberService memberService;

    @InjectMocks
    private AssetGrantApiService service;

    /* ---------------- 实物 ---------------- */

    @Test
    @DisplayName("实物：收件三要素落到履约单上，source_type=MALL")
    void 实物履约单带着收件信息() {
        when(memberService.requireMemberName(anyLong())).thenReturn("tester");
        doAnswer(inv -> {
            inv.getArgument(0, PhysicalDelivery.class).setId(777L);
            return 1;
        }).when(physicalDeliveryDao).insert(any(PhysicalDelivery.class));

        AssetGrantResult result = service.grant(physicalCmd("张三", "13800000000", "上海市黄浦区XX路1号"));

        ArgumentCaptor<PhysicalDelivery> captor = ArgumentCaptor.forClass(PhysicalDelivery.class);
        verify(physicalDeliveryDao).insert(captor.capture());
        PhysicalDelivery saved = captor.getValue();
        assertAll(
                () -> assertTrue(result.accepted()),
                // fulfill_ref_id 是「东西发到哪去了」的唯一线索，不能是空
                () -> assertEquals("777", result.fulfillRefId()),
                () -> assertEquals("MALL", saved.getSourceType()),
                () -> assertEquals(ORDER_NO, saved.getSourceBizId()),
                () -> assertEquals("张三", saved.getReceiverName()),
                () -> assertEquals("上海市黄浦区XX路1号", saved.getReceiverAddress()));
    }

    @Test
    @DisplayName("🔴 实物缺收件信息 → 拒绝，绝不拿空地址建履约单")
    void 实物缺地址就拒绝() {
        AssetGrantResult result = service.grant(physicalCmd("张三", "13800000000", null));

        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(AssetGrantReason.RECEIVER_REQUIRED, result.reason()),
                /*
                 * 建一张没有地址的履约单，它会一直躺在运营的发货台上，
                 * 而没有任何人知道该寄到哪 —— 比发不出去更糟。
                 */
                () -> verify(physicalDeliveryDao, never()).insert(any(PhysicalDelivery.class)));
    }

    @Test
    @DisplayName("🔴 实物重复投递撞唯一键 → 幂等成功，并回原来那条的 id")
    void 实物重复投递算成功() {
        when(memberService.requireMemberName(anyLong())).thenReturn("tester");
        doThrow(new DuplicateKeyException("uk_t_biz_phy_dlv_src"))
                .when(physicalDeliveryDao).insert(any(PhysicalDelivery.class));
        PhysicalDelivery existing = new PhysicalDelivery();
        existing.setId(888L);
        when(physicalDeliveryDao.selectOne(any())).thenReturn(existing);

        AssetGrantResult result = service.grant(physicalCmd("张三", "138", "地址"));

        assertAll(
                // 单子已经在了，运营照样发得出去 —— 判失败会让订单被标成「履约失败」
                () -> assertTrue(result.accepted()),
                // 不回 id 的话订单的 fulfill_ref_id 是空的，等于丢了线索
                () -> assertEquals("888", result.fulfillRefId()));
    }

    /* ---------------- 券 ---------------- */

    @Test
    @DisplayName("🔴 券：兑 3 张就落 3 行，来源单号带 :1 :2 :3 后缀")
    void 券按份数逐行发放() {
        when(memberService.requireMemberName(anyLong())).thenReturn("tester");
        doAnswer(inv -> {
            MemberCoupon c = inv.getArgument(0, MemberCoupon.class);
            if (c.getId() == null) {
                c.setId(100L);
            }
            return 1;
        }).when(memberCouponDao).insert(any(MemberCoupon.class));

        AssetGrantResult result = service.grant(couponCmd("SUMMER2026", "夏日券", 3));

        ArgumentCaptor<MemberCoupon> captor = ArgumentCaptor.forClass(MemberCoupon.class);
        // 券是实例类资产：份数不能乘进金额里，只能一份一行
        verify(memberCouponDao, times(3)).insert(captor.capture());
        List<MemberCoupon> saved = captor.getAllValues();
        assertAll(
                () -> assertTrue(result.accepted()),
                () -> assertEquals(ORDER_NO + ":1", saved.get(0).getSourceBizId()),
                () -> assertEquals(ORDER_NO + ":3", saved.get(2).getSourceBizId()),
                // 券名直接显示给用户，必须是商品名而不是编码
                () -> assertEquals("夏日券", saved.get(0).getCouponName()),
                () -> assertEquals("SUMMER2026", saved.get(0).getCouponCode()),
                () -> assertEquals("MALL", saved.get(0).getSourceType()));
    }

    @Test
    @DisplayName("券名取不到时回退用券模编码，不拿备注顶替")
    void 券名回退用编码() {
        when(memberService.requireMemberName(anyLong())).thenReturn("tester");
        service.grant(couponCmd("SUMMER2026", "  ", 1));

        ArgumentCaptor<MemberCoupon> captor = ArgumentCaptor.forClass(MemberCoupon.class);
        verify(memberCouponDao).insert(captor.capture());
        /*
         * 线上出过的事故：拿 remark 顶替券名，而 remark 被固定写成「提案生成成功」，
         * 于是发出去的券全都叫那个名字。编码难看但稳定可追溯。
         */
        assertEquals("SUMMER2026", captor.getValue().getCouponName());
    }

    @Test
    @DisplayName("🔴 券商品漏配券模 → 拒绝而不是抛，重试一万次也发不出来")
    void 券缺券模就拒绝() {
        AssetGrantResult result = service.grant(couponCmd(null, "夏日券", 1));

        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(AssetGrantReason.ASSET_REF_REQUIRED, result.reason()),
                () -> verify(memberCouponDao, never()).insert(any(MemberCoupon.class)));
    }

    /* ---------------- 钱包 ---------------- */

    @Test
    @DisplayName("现金：实发 = 单份面额 × 份数")
    void 钱包按份数乘面额() {
        service.grant(walletCmd(new BigDecimal("5.00"), 3));

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(memberWalletService).executeWalletRefund(
                anyLong(), any(PrizeTypeEnum.class), amount.capture(), anyString(), anyString(), anyString());
        assertEquals(0, new BigDecimal("15.00").compareTo(amount.getValue()));
    }

    @Test
    @DisplayName("现金面额非正 → 拒绝，不入账")
    void 面额非正就拒绝() {
        AssetGrantResult result = service.grant(walletCmd(BigDecimal.ZERO, 1));

        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(AssetGrantReason.AMOUNT_INVALID, result.reason()),
                () -> verify(memberWalletService, never())
                        .executeWalletRefund(any(), any(), any(), any(), any(), any()));
    }

    /* ---------------- 拒绝 vs 故障 ---------------- */

    @Test
    @DisplayName("🔴 并发冲突要上抛，不能翻成拒绝 —— 它是可重试的")
    void 并发冲突上抛而不是翻译() {
        doThrow(new BusinessException(BizErrorCode.ACCOUNT_BALANCE_CHANGED))
                .when(memberWalletService)
                .executeWalletRefund(any(), any(), any(), any(), any(), any());

        /*
         * 翻成拒绝的话，调用方会把订单标成 60-履约失败并停在那里，
         * 而它其实只是撞上了一次乐观锁 —— 再试一次就成了。
         * 上抛让整个履约事务回滚，单子退回「待履约」等下一轮。
         */
        assertThrows(BusinessException.class, () -> service.grant(walletCmd(BigDecimal.TEN, 1)));
    }

    @Test
    @DisplayName("钱包冻结 → 拒绝（人工介入才能解，重试没用）")
    void 钱包冻结翻成拒绝() {
        doThrow(new BusinessException("账户已冻结"))
                .when(memberWalletService)
                .executeWalletRefund(any(), any(), any(), any(), any(), any());

        AssetGrantResult result = service.grant(walletCmd(BigDecimal.TEN, 1));

        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(AssetGrantReason.WALLET_UNAVAILABLE, result.reason()));
    }

    @Test
    @DisplayName("未知资产类型 → 拒绝，不表现成 500")
    void 未知类型不抛异常() {
        AssetGrantResult result = service.grant(new AssetGrantCmd(
                MEMBER_ID, "NOT_A_TYPE", null, null, 1, null,
                "MALL", ORDER_NO, "MALL_EXCHANGE", null, null, null, null));

        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals(AssetGrantReason.UNSUPPORTED_ASSET_TYPE, result.reason()));
    }

    @Test
    @DisplayName("🔴 MARKER / LOTTERY / CUSTOM 没有发放通道 → 拒绝，不假装成功")
    void 没有通道的类型不假装成功() {
        for (String type : List.of("MARKER", "LOTTERY", "CUSTOM")) {
            AssetGrantResult result = service.grant(new AssetGrantCmd(
                    MEMBER_ID, type, null, null, 1, null,
                    "MALL", ORDER_NO, "MALL_EXCHANGE", null, null, null, null));
            /*
             * 假装成功的表现是：用户的订单显示「已完成」，而他手里什么都没有，
             * 且没有任何一条记录说明本该发什么。
             */
            assertFalse(result.accepted(), type + " 不该被判成发放成功");
            assertEquals(AssetGrantReason.UNSUPPORTED_ASSET_TYPE, result.reason(), type);
        }
    }

    /* ---------------- 造数 ---------------- */

    private static AssetGrantCmd physicalCmd(String name, String phone, String address) {
        return new AssetGrantCmd(MEMBER_ID, "PHYSICAL", null, "限量T恤", 1, null,
                "MALL", ORDER_NO, "MALL_EXCHANGE", name, phone, address, "商城兑换");
    }

    private static AssetGrantCmd couponCmd(String assetRef, String assetName, int quantity) {
        return new AssetGrantCmd(MEMBER_ID, "COUPON", assetRef, assetName, quantity, null,
                "MALL", ORDER_NO, "MALL_EXCHANGE", null, null, null, "商城兑换");
    }

    private static AssetGrantCmd walletCmd(BigDecimal amount, int quantity) {
        return new AssetGrantCmd(MEMBER_ID, "BALANCE", null, "5元红包", quantity, amount,
                "MALL", ORDER_NO, "MALL_EXCHANGE", null, null, null, "商城兑换");
    }
}
