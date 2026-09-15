package solvela.ledger.coupon.writeoff;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.coupon.CouponWriteOff;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;
import solvela.enums.CouponScopeTypeEnum;
import solvela.enums.CouponStatusEnum;
import solvela.enums.CouponWriteOffActionEnum;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.template.dao.CouponWriteOffDao;
import solvela.ledger.coupon.writeoff.domain.CouponTrialCmd;
import solvela.ledger.coupon.writeoff.domain.CouponTrialResult;
import solvela.ledger.coupon.writeoff.domain.CouponUnusableReason;
import solvela.ledger.coupon.writeoff.domain.CouponWriteOffResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 三阶段核销。
 *
 * <h3>这里守的是两件事</h3>
 * <ol>
 *   <li><b>试算选对券</b> —— 选错不会报错，只是用户少减了钱或平台多减了钱；</li>
 *   <li><b>状态机不漏钱</b> —— 锁定/确认/释放三个动作的失败与幂等必须分得开。
 *       混成一种，要么重复提交被当失败拒掉，要么抢券失败被当成功放行。</li>
 * </ol>
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponWriteOffServiceTest {

    private static final Long MEMBER_ID = 1001L;
    private static final String ORDER_NO = "M20260915120000123ABC";

    @Mock
    private MemberCouponDao memberCouponDao;
    @Mock
    private CouponWriteOffDao couponWriteOffDao;

    private CouponWriteOffService service;

    @BeforeEach
    void setUp() {
        service = new CouponWriteOffService(memberCouponDao, couponWriteOffDao);
    }

    /* ---------------- 试算 ---------------- */

    @Test
    @DisplayName("🔴 最优券逐张算出来，不走「百分比一定更划算」这种捷径")
    void 选出最优券() {
        MemberCoupon fixed = coupon(1L, "满100减20", CouponDiscountTypeEnum.FIXED, "20", null, "100");
        MemberCoupon percent = coupon(2L, "8折最高减50", CouponDiscountTypeEnum.PERCENT, "20", "50", "0");
        when(memberCouponDao.selectUsableCandidates(anyLong(), any())).thenReturn(List.of(fixed, percent));

        // 150 元：固定额 20，百分比 min(30,50)=30 —— 百分比赢
        CouponTrialResult result = service.trial(mallCmd("150"));

        assertAll(
                () -> assertEquals(2, result.usable().size()),
                () -> assertEquals(2L, result.recommended().couponId()),
                () -> assertEquals(new BigDecimal("30.00"), result.recommended().discountAmount()),
                () -> assertTrue(result.unusable().isEmpty()));
    }

    @Test
    @DisplayName("🔴 ④ 平局选先过期的那张 —— 否则用户攒一堆快过期的券却总在用新的")
    void 平局选先过期的() {
        MemberCoupon later = coupon(1L, "后过期", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        later.setValidEndTime(LocalDateTime.now().plusDays(30));
        MemberCoupon sooner = coupon(2L, "先过期", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        sooner.setValidEndTime(LocalDateTime.now().plusDays(3));
        when(memberCouponDao.selectUsableCandidates(anyLong(), any())).thenReturn(List.of(later, sooner));

        assertEquals(2L, service.trial(mallCmd("150")).recommended().couponId());
    }

    @Test
    @DisplayName("🔴 用不了的券也要返回，带上原因 —— 券凭空消失，用户第一反应是系统坏了")
    void 不可用的券带着原因返回() {
        MemberCoupon below = coupon(1L, "满500减50", CouponDiscountTypeEnum.FIXED, "50", null, "500");
        MemberCoupon noRule = coupon(2L, "没规则的券", null, null, null, null);
        MemberCoupon scoreCoupon = coupon(3L, "抵积分的券", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        scoreCoupon.setDeductTarget(CouponDeductTargetEnum.SCORE);
        when(memberCouponDao.selectUsableCandidates(anyLong(), any()))
                .thenReturn(List.of(below, noRule, scoreCoupon));

        CouponTrialResult result = service.trial(mallCmd("150"));

        assertAll(
                () -> assertTrue(result.usable().isEmpty()),
                () -> assertNull(result.recommended()),
                () -> assertEquals(3, result.unusable().size()),
                () -> assertEquals(CouponUnusableReason.BELOW_MIN_AMOUNT, result.unusable().get(0).reason()),
                // 规则列为 NULL = 没有模板可读，和「配成了减 0」是两回事
                () -> assertEquals(CouponUnusableReason.NO_RULE, result.unusable().get(1).reason()),
                // 1 积分 ≠ 1 元，不替用户跨类换算
                () -> assertEquals(CouponUnusableReason.DEDUCT_TARGET_MISMATCH,
                        result.unusable().get(2).reason()));
    }

    @Test
    @DisplayName("适用范围：指定类目的券只在那个类目上可用，而且匹配不吃前缀")
    void 范围匹配() {
        MemberCoupon scoped = coupon(1L, "数码专用", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        scoped.setScopeType(CouponScopeTypeEnum.CATEGORY);
        scoped.setScopeRefs("[\"CAT1\"]");
        when(memberCouponDao.selectUsableCandidates(anyLong(), any())).thenReturn(List.of(scoped));

        assertEquals(1, service.trial(
                CouponTrialCmd.forMall(MEMBER_ID, new BigDecimal("150"),
                        CouponDeductTargetEnum.CASH, "SKU1", "CAT1")).usable().size());

        // CAT10 不该命中 CAT1 —— 所以匹配时两边都裹上引号
        assertEquals(1, service.trial(
                CouponTrialCmd.forMall(MEMBER_ID, new BigDecimal("150"),
                        CouponDeductTargetEnum.CASH, "SKU1", "CAT10")).unusable().size());
    }

    @Test
    @DisplayName("⚠️ 限定了范围却没给明细 → 判不可用，不是放行")
    void 范围没有明细时不放行() {
        MemberCoupon broken = coupon(1L, "脏数据券", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        broken.setScopeType(CouponScopeTypeEnum.COMMODITY);
        broken.setScopeRefs(null);
        when(memberCouponDao.selectUsableCandidates(anyLong(), any())).thenReturn(List.of(broken));

        // 放行等于把一张「本该限定」的券变成了全场通用券
        assertEquals(CouponUnusableReason.SCOPE_MISMATCH,
                service.trial(mallCmd("150")).unusable().get(0).reason());
    }

    /* ---------------- 锁定 ---------------- */

    @Test
    @DisplayName("锁定成功：写一行 LOCK 流水，抵扣额当场定死")
    void 锁定成功并落流水() {
        when(memberCouponDao.lockCoupon(anyLong(), anyLong(), anyString(), any(), any())).thenReturn(1);
        when(memberCouponDao.selectById(1L)).thenReturn(coupon(1L, "券", CouponDiscountTypeEnum.FIXED, "20", null, "0"));

        CouponWriteOffResult result = service.lock(1L, MEMBER_ID, "MALL", ORDER_NO, null,
                new BigDecimal("150"), new BigDecimal("20"));

        ArgumentCaptor<CouponWriteOff> captor = ArgumentCaptor.forClass(CouponWriteOff.class);
        verify(couponWriteOffDao).insert(captor.capture());
        CouponWriteOff flow = captor.getValue();
        assertAll(
                () -> assertTrue(result.ok()),
                () -> assertFalse(result.idempotent()),
                () -> assertEquals(CouponWriteOffActionEnum.LOCK, flow.getAction()),
                () -> assertEquals(ORDER_NO, flow.getBizRefId()),
                // 🔴 抵扣前和实际抵扣都要落地：不落的话退款不知道退多少、财务对不了账
                () -> assertEquals(new BigDecimal("150"), flow.getOriginalAmount()),
                () -> assertEquals(new BigDecimal("20"), flow.getDiscountAmount()),
                () -> assertEquals(MEMBER_ID, flow.getMemberId()));
    }

    @Test
    @DisplayName("🔴 同一笔单重复锁定 = 幂等成功；被【别人】锁走 = 失败。两者必须分得开")
    void 锁定的幂等与失败分得开() {
        when(memberCouponDao.lockCoupon(anyLong(), anyLong(), anyString(), any(), any())).thenReturn(0);

        // ① 已经是自己锁的 —— 重复提交、重试，是幂等成功
        MemberCoupon mine = coupon(1L, "券", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        mine.setStatus(CouponStatusEnum.LOCKED);
        mine.setLockedBizId(ORDER_NO);
        mine.setDiscountAmount(new BigDecimal("20"));
        when(memberCouponDao.selectById(1L)).thenReturn(mine);

        CouponWriteOffResult same = service.lock(1L, MEMBER_ID, "MALL", ORDER_NO, null,
                new BigDecimal("150"), new BigDecimal("20"));
        assertAll(
                () -> assertTrue(same.ok()),
                () -> assertTrue(same.idempotent(), "调用方据此知道别再重复做后续动作"),
                () -> assertEquals(new BigDecimal("20"), same.discountAmount()));

        // ② 被另一笔锁走了 —— 失败，别再试
        MemberCoupon theirs = coupon(1L, "券", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        theirs.setStatus(CouponStatusEnum.LOCKED);
        theirs.setLockedBizId("ANOTHER_ORDER");
        when(memberCouponDao.selectById(1L)).thenReturn(theirs);

        assertFalse(service.lock(1L, MEMBER_ID, "MALL", ORDER_NO, null,
                new BigDecimal("150"), new BigDecimal("20")).ok());
    }

    /* ---------------- 确认 / 释放 ---------------- */

    @Test
    @DisplayName("确认成功：4-锁定中 → 1-已使用，落一行 CONFIRM，金额抄自锁定那一行")
    void 确认成功() {
        when(memberCouponDao.confirmCoupon(anyLong(), anyString(), any())).thenReturn(1);
        MemberCoupon used = coupon(1L, "券", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        used.setStatus(CouponStatusEnum.USED);
        used.setDiscountAmount(new BigDecimal("20"));
        when(memberCouponDao.selectById(1L)).thenReturn(used);
        when(couponWriteOffDao.selectLockRow(1L, ORDER_NO)).thenReturn(lockRow());

        CouponWriteOffResult result = service.confirm(1L, "MALL", ORDER_NO);

        ArgumentCaptor<CouponWriteOff> captor = ArgumentCaptor.forClass(CouponWriteOff.class);
        verify(couponWriteOffDao).insert(captor.capture());
        assertAll(
                () -> assertTrue(result.ok()),
                () -> assertEquals(CouponWriteOffActionEnum.CONFIRM, captor.getValue().getAction()),
                // 抵扣前金额抄自 LOCK 行 —— 让每个调用方自己带金额进来，漏传的表现是
                // 流水里的金额是个编出来的数，要等对账时才发现
                () -> assertEquals(new BigDecimal("150.00"), captor.getValue().getOriginalAmount()));
    }

    @Test
    @DisplayName("🔴 确认不上（多半是兜底任务已经把券放回去了）→ 失败，调用方必须按「没用券」重算")
    void 确认不上时明确失败() {
        when(memberCouponDao.confirmCoupon(anyLong(), anyString(), any())).thenReturn(0);
        MemberCoupon released = coupon(1L, "券", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        released.setStatus(CouponStatusEnum.UNUSED);
        when(memberCouponDao.selectById(1L)).thenReturn(released);

        CouponWriteOffResult result = service.confirm(1L, "MALL", ORDER_NO);

        // 当没看见的话：订单按打折价结算，而券还躺在券包里可以再用一次
        assertFalse(result.ok());
        verify(couponWriteOffDao, never()).insert(any(CouponWriteOff.class));
    }

    @Test
    @DisplayName("释放：券放回未使用，落一行 RELEASE 并记下原因")
    void 释放成功并记原因() {
        MemberCoupon locked = coupon(1L, "券", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        locked.setStatus(CouponStatusEnum.LOCKED);
        locked.setDiscountAmount(new BigDecimal("20"));
        when(memberCouponDao.selectById(1L)).thenReturn(locked);
        when(memberCouponDao.releaseCoupon(eq(1L), eq(ORDER_NO))).thenReturn(1);
        when(couponWriteOffDao.selectLockRow(1L, ORDER_NO)).thenReturn(lockRow());

        CouponWriteOffResult result = service.release(1L, "MALL", ORDER_NO, "订单超时取消");

        ArgumentCaptor<CouponWriteOff> captor = ArgumentCaptor.forClass(CouponWriteOff.class);
        verify(couponWriteOffDao).insert(captor.capture());
        assertAll(
                () -> assertTrue(result.ok()),
                () -> assertEquals(CouponWriteOffActionEnum.RELEASE, captor.getValue().getAction()),
                () -> assertEquals("订单超时取消", captor.getValue().getRemark()),
                /*
                 * 🔴 释放会清掉券行上的 discount_amount，所以流水里这个数必须是
                 *    【清之前】抄下来的。抄晚了就永远是 0，而那意味着
                 *    「锁了又释放」这件事的金额痕迹没了。
                 */
                () -> assertEquals(new BigDecimal("20"), captor.getValue().getDiscountAmount()));
    }

    @Test
    @DisplayName("重复释放 = 幂等成功：两条取消路径同时跑到是常态")
    void 重复释放是幂等成功() {
        MemberCoupon free = coupon(1L, "券", CouponDiscountTypeEnum.FIXED, "20", null, "0");
        free.setStatus(CouponStatusEnum.UNUSED);
        when(memberCouponDao.selectById(1L)).thenReturn(free);
        when(memberCouponDao.releaseCoupon(anyLong(), anyString())).thenReturn(0);

        CouponWriteOffResult result = service.release(1L, "MALL", ORDER_NO, "重复取消");

        assertAll(
                () -> assertTrue(result.ok()),
                () -> assertTrue(result.idempotent()),
                // 幂等不该再补一行流水 —— 否则一次释放会留下 N 行，对账时看着像放了 N 次
                () -> verify(couponWriteOffDao, never()).insert(any(CouponWriteOff.class)));
    }

    /* ---------------- fixtures ---------------- */

    private static CouponTrialCmd mallCmd(String payAmount) {
        return CouponTrialCmd.forMall(MEMBER_ID, new BigDecimal(payAmount),
                CouponDeductTargetEnum.CASH, "SKU1", "CAT1");
    }

    private static CouponWriteOff lockRow() {
        CouponWriteOff row = new CouponWriteOff();
        row.setOriginalAmount(new BigDecimal("150.00"));
        row.setDiscountAmount(new BigDecimal("20.00"));
        return row;
    }

    private static MemberCoupon coupon(Long id, String name, CouponDiscountTypeEnum type,
                                       String value, String maxDiscount, String minAmount) {
        MemberCoupon coupon = new MemberCoupon();
        coupon.setId(id);
        coupon.setMemberId(MEMBER_ID);
        coupon.setCouponCode("C" + id);
        coupon.setCouponName(name);
        coupon.setStatus(CouponStatusEnum.UNUSED);
        coupon.setDiscountType(type);
        coupon.setDiscountValue(value == null ? null : new BigDecimal(value));
        coupon.setMaxDiscount(maxDiscount == null ? null : new BigDecimal(maxDiscount));
        coupon.setMinAmount(minAmount == null ? null : new BigDecimal(minAmount));
        coupon.setDeductTarget(type == null ? null : CouponDeductTargetEnum.CASH);
        coupon.setScopeType(CouponScopeTypeEnum.ALL);
        coupon.setValidEndTime(LocalDateTime.now().plusDays(10));
        return coupon;
    }
}
