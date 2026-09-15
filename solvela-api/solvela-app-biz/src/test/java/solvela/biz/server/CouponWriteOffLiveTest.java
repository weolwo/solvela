package solvela.biz.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;
import solvela.enums.CouponScopeTypeEnum;
import solvela.enums.CouponStatusEnum;
import solvela.enums.CouponWriteOffActionEnum;
import solvela.coupon.CouponWriteOff;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.template.dao.CouponWriteOffDao;
import solvela.ledger.coupon.writeoff.CouponWriteOffService;
import solvela.ledger.coupon.writeoff.domain.CouponTrialCmd;
import solvela.ledger.coupon.writeoff.domain.CouponTrialResult;
import solvela.ledger.coupon.writeoff.domain.CouponWriteOffResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三阶段核销的<b>真库</b>验证：锁定 → 确认 / 释放，走的是真的条件更新。
 *
 * <h3>为什么真库</h3>
 * {@code CouponWriteOffServiceTest} 用 mock 把编排层的分支覆盖过了，但它
 * <b>验不了那三条 WHERE</b> —— 而那三条 WHERE 就是并发安全本身：
 * {@code WHERE status = 0} 少了，两笔订单会同时锁上同一张券；
 * {@code AND locked_biz_id = ?} 少了，A 单锁的券会被 B 单确认掉。
 * mock 里 {@code lockCoupon} 返回什么都是我写的，证明不了 SQL 是对的。
 *
 * <h3>🔴 {@code @Transactional} 回滚</h3>
 * 本测试往 {@code t_member_coupon} 和 {@code t_coupon_write_off} 里真写数据。
 * 前者是券包（用户看得到），后者是对账流水 —— 两张表都不能留测试垃圾。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@SpringBootTest
@Transactional
class CouponWriteOffLiveTest {

    /** 用一个不存在的会员号，免得试算把真实用户的券也捞进来 */
    private static final Long TEST_MEMBER_ID = -90001L;
    private static final String ORDER_A = "ZZTEST-ORDER-A";
    private static final String ORDER_B = "ZZTEST-ORDER-B";

    @Autowired
    private CouponWriteOffService couponWriteOffService;

    @Autowired
    private MemberCouponDao memberCouponDao;

    @Autowired
    private CouponWriteOffDao couponWriteOffDao;

    @Test
    @DisplayName("🔴 锁定 → 确认：券变已使用，两行流水，抵扣额一路带到底")
    void 锁定后确认() {
        MemberCoupon coupon = insertCoupon();

        CouponWriteOffResult locked = couponWriteOffService.lock(
                coupon.getId(), TEST_MEMBER_ID, "MALL", ORDER_A, null,
                new BigDecimal("150.00"), new BigDecimal("20.00"));
        assertTrue(locked.ok());

        MemberCoupon afterLock = memberCouponDao.selectById(coupon.getId());
        assertAll("锁定之后",
                () -> assertEquals(CouponStatusEnum.LOCKED, afterLock.getStatus()),
                () -> assertEquals(ORDER_A, afterLock.getLockedBizId()),
                () -> assertNotNull(afterLock.getLockedTime()),
                () -> assertEquals(0, new BigDecimal("20.00").compareTo(afterLock.getDiscountAmount())));

        CouponWriteOffResult confirmed = couponWriteOffService.confirm(coupon.getId(), "MALL", ORDER_A);
        assertTrue(confirmed.ok());

        MemberCoupon afterConfirm = memberCouponDao.selectById(coupon.getId());
        assertAll("确认之后",
                () -> assertEquals(CouponStatusEnum.USED, afterConfirm.getStatus()),
                () -> assertNotNull(afterConfirm.getUsedTime()),
                // locked_biz_id 刻意保留：券用掉之后还要能回答「是被哪一笔用掉的」
                () -> assertEquals(ORDER_A, afterConfirm.getLockedBizId()));

        List<CouponWriteOff> flows = couponWriteOffDao.selectByCoupon(coupon.getId());
        assertAll("流水",
                // 🔴 三个动作各记一行。只记 CONFIRM 的话「锁了又释放」就查不到了
                () -> assertEquals(2, flows.size()),
                () -> assertEquals(CouponWriteOffActionEnum.CONFIRM, flows.get(0).getAction()),
                () -> assertEquals(CouponWriteOffActionEnum.LOCK, flows.get(1).getAction()),
                // 抵扣前金额：确认那一行是从锁定那一行抄来的，不是调用方带进来的
                () -> assertEquals(0, new BigDecimal("150.00").compareTo(flows.get(0).getOriginalAmount())),
                () -> assertEquals(0, new BigDecimal("20.00").compareTo(flows.get(0).getDiscountAmount())));

        // 对账入口：按单号反查这一单用了哪张券、减了多少
        assertEquals(2, couponWriteOffDao.selectByBiz("MALL", ORDER_A).size());
    }

    @Test
    @DisplayName("🔴 锁定 → 释放：券回到未使用，而且能被【下一笔】重新锁上")
    void 锁定后释放还能再用() {
        MemberCoupon coupon = insertCoupon();

        couponWriteOffService.lock(coupon.getId(), TEST_MEMBER_ID, "MALL", ORDER_A, null,
                new BigDecimal("150.00"), new BigDecimal("20.00"));
        CouponWriteOffResult released = couponWriteOffService.release(
                coupon.getId(), "MALL", ORDER_A, "订单超时取消");
        assertTrue(released.ok());

        MemberCoupon afterRelease = memberCouponDao.selectById(coupon.getId());
        assertAll("释放之后是一张干净的未使用券",
                () -> assertEquals(CouponStatusEnum.UNUSED, afterRelease.getStatus()),
                () -> assertNull(afterRelease.getLockedBizId()),
                () -> assertNull(afterRelease.getLockedTime()),
                () -> assertNull(afterRelease.getDiscountAmount()));

        // 这正是三阶段存在的意义：订单没成，券要回得来，而且回来之后还能用
        assertTrue(couponWriteOffService.lock(coupon.getId(), TEST_MEMBER_ID, "MALL", ORDER_B, null,
                new BigDecimal("200.00"), new BigDecimal("20.00")).ok());

        // 释放那一行必须带着金额：锁了又释放，这件事的金额痕迹不能丢
        List<CouponWriteOff> flows = couponWriteOffDao.selectByCoupon(coupon.getId());
        CouponWriteOff release = flows.stream()
                .filter(f -> f.getAction() == CouponWriteOffActionEnum.RELEASE)
                .findFirst().orElseThrow();
        assertAll(
                () -> assertEquals(0, new BigDecimal("20.00").compareTo(release.getDiscountAmount())),
                () -> assertEquals("订单超时取消", release.getRemark()));
    }

    @Test
    @DisplayName("🔴 并发闸：一张券被 A 单锁上之后，B 单锁不上 —— 这就是那条 WHERE status = 0")
    void 同一张券不会被两单同时锁上() {
        MemberCoupon coupon = insertCoupon();

        assertTrue(couponWriteOffService.lock(coupon.getId(), TEST_MEMBER_ID, "MALL", ORDER_A, null,
                new BigDecimal("150.00"), new BigDecimal("20.00")).ok());

        CouponWriteOffResult second = couponWriteOffService.lock(coupon.getId(), TEST_MEMBER_ID,
                "MALL", ORDER_B, null, new BigDecimal("150.00"), new BigDecimal("20.00"));
        assertFalse(second.ok(), "同一张券被两单锁上，就是一张券被用了两次");

        // 而且 B 单也确认不了它 —— locked_biz_id 必须对上
        assertFalse(couponWriteOffService.confirm(coupon.getId(), "MALL", ORDER_B).ok());
    }

    @Test
    @DisplayName("重复锁定同一笔单 = 幂等成功（重复提交 / 失败重试都会走到）")
    void 同一笔单重复锁定是幂等的() {
        MemberCoupon coupon = insertCoupon();

        couponWriteOffService.lock(coupon.getId(), TEST_MEMBER_ID, "MALL", ORDER_A, null,
                new BigDecimal("150.00"), new BigDecimal("20.00"));
        CouponWriteOffResult again = couponWriteOffService.lock(coupon.getId(), TEST_MEMBER_ID,
                "MALL", ORDER_A, null, new BigDecimal("150.00"), new BigDecimal("20.00"));

        assertAll(
                () -> assertTrue(again.ok()),
                () -> assertTrue(again.idempotent()),
                // 幂等不补流水，否则一次锁定会留下 N 行，对账时看着像锁了 N 次
                () -> assertEquals(1, couponWriteOffDao.selectByCoupon(coupon.getId()).size()));
    }

    @Test
    @DisplayName("试算：真库里捞券、算抵扣、选最优 —— 门槛不够的带着原因回来")
    void 试算走真库() {
        MemberCoupon cheap = insertCoupon();          // 无门槛减 20
        MemberCoupon steep = insertCoupon();          // 满 500 减 50
        steep.setCouponName("满500减50");
        steep.setDiscountValue(new BigDecimal("50.00"));
        steep.setMinAmount(new BigDecimal("500.00"));
        memberCouponDao.updateById(steep);

        CouponTrialResult result = couponWriteOffService.trial(CouponTrialCmd.forMall(
                TEST_MEMBER_ID, new BigDecimal("150.00"), CouponDeductTargetEnum.CASH, "SKU1", "CAT1"));

        assertAll(
                () -> assertEquals(1, result.usable().size()),
                () -> assertEquals(cheap.getId(), result.recommended().couponId()),
                () -> assertEquals(0, new BigDecimal("20.00").compareTo(result.recommended().discountAmount())),
                // 用不了的那张也回来了，带着原因 —— 券凭空消失才是最让用户困惑的
                () -> assertEquals(1, result.unusable().size()),
                () -> assertEquals(steep.getId(), result.unusable().get(0).couponId()));
    }

    /** 造一张无门槛减 20 的现金券，直接入库 */
    private MemberCoupon insertCoupon() {
        MemberCoupon coupon = new MemberCoupon();
        coupon.setMemberId(TEST_MEMBER_ID);
        coupon.setMemberName("zz-test");
        coupon.setCouponCode("ZZTESTCOUPON");
        coupon.setCouponType("GENERAL");
        coupon.setCouponName("单元测试用券");
        coupon.setStatus(CouponStatusEnum.UNUSED);
        coupon.setSourceType("TEST");
        coupon.setSourceBizId("live-test");
        coupon.setValidStartTime(LocalDateTime.now().minusDays(1));
        coupon.setValidEndTime(LocalDateTime.now().plusDays(10));
        coupon.setTemplateVersion(1);
        coupon.setDiscountType(CouponDiscountTypeEnum.FIXED);
        coupon.setDiscountValue(new BigDecimal("20.00"));
        coupon.setMinAmount(BigDecimal.ZERO);
        coupon.setDeductTarget(CouponDeductTargetEnum.CASH);
        coupon.setScopeType(CouponScopeTypeEnum.ALL);
        memberCouponDao.insert(coupon);
        return coupon;
    }
}
