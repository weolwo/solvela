package solvela.biz.server;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.CouponStatusEnum;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.dao.MemberCouponDao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 人工发券的<b>防重索引</b>是不是真的在。
 *
 * <h3>为什么这条必须打在真库上</h3>
 * {@code CouponManualGrantServiceTest} 里的「重复提交算跳过」是靠 mock 抛
 * {@code DuplicateKeyException} 演出来的 —— 那证明的是<b>接住异常之后的处理对</b>，
 * 证明不了<b>异常真的会被抛出来</b>。
 *
 * <p>而后者恰恰是最容易悄悄失效的一环：{@code uk_manual_src} 是一个<b>函数索引</b>
 *（{@code CASE WHEN source_type='MANUAL' THEN source_biz_id END}），
 * 谁把它删了、或者新环境忘了执行那个脚本，代码这一侧一个字都不用改就能跑 ——
 * 表现是运营双击一次多发一批券，而且没有任何报错。
 *
 * <h3>🔴 它还要验另一半：非 MANUAL 的重复必须<b>照旧允许</b></h3>
 * 库里已经有 53 组 {@code PROPOSAL} 的重复（早期造数脚本留下的）。
 * 如果哪天有人把这个索引「顺手改成」全表唯一键，发奖那条路会当场开始报错。
 *
 * <p>⚠️ {@code @Transactional} 回滚：本测试真往券表里写行。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@SpringBootTest
@Transactional
class CouponManualGrantLiveTest {

    private static final Long TEST_MEMBER_ID = -90002L;
    private static final String TICKET = "ZZTEST-TICKET-001";

    @Autowired
    private MemberCouponDao memberCouponDao;

    @Test
    @DisplayName("🔴 同一个工单对同一个人重复发 → 被数据库挡住（uk_manual_src 真的在）")
    void 人工发券重复会被挡住() {
        String sourceBizId = TICKET + ":" + TEST_MEMBER_ID + ":1";

        memberCouponDao.insert(manualCoupon(sourceBizId));

        // 挡不住的话，运营双击一次就多发一批券出去，而且没有任何报错
        assertThrows(DuplicateKeyException.class,
                () -> memberCouponDao.insert(manualCoupon(sourceBizId)));
    }

    @Test
    @DisplayName("同一个工单发给【不同的人】不冲突 —— 所以幂等键里必须有会员号")
    void 不同会员不冲突() {
        assertDoesNotThrow(() -> {
            memberCouponDao.insert(manualCoupon(TICKET + ":-90002:1"));
            memberCouponDao.insert(manualCoupon(TICKET + ":-90003:1"));
            // 同一个人的第 2 张也不冲突：序号在键里
            memberCouponDao.insert(manualCoupon(TICKET + ":-90002:2"));
        });

        List<MemberCoupon> rows = memberCouponDao.selectList(Wrappers.<MemberCoupon>lambdaQuery()
                .likeRight(MemberCoupon::getSourceBizId, TICKET));
        assertEquals(3, rows.size());
    }

    @Test
    @DisplayName("🔴 非 MANUAL 的重复照旧允许 —— 库里有 53 组 PROPOSAL 重复，改成全表唯一键会当场炸")
    void 非人工来源的重复不受影响() {
        MemberCoupon first = manualCoupon("ZZTEST-PROPOSAL-1");
        first.setSourceType("PROPOSAL");
        MemberCoupon second = manualCoupon("ZZTEST-PROPOSAL-1");
        second.setSourceType("PROPOSAL");

        assertAll(
                () -> assertDoesNotThrow(() -> memberCouponDao.insert(first)),
                () -> assertDoesNotThrow(() -> memberCouponDao.insert(second)));
    }

    /** 造一张 MANUAL 来源的券。规则不重要，这几条验的是索引 */
    private static MemberCoupon manualCoupon(String sourceBizId) {
        MemberCoupon coupon = new MemberCoupon();
        coupon.setMemberId(TEST_MEMBER_ID);
        coupon.setMemberName("zz-test");
        coupon.setCouponCode("ZZTESTCOUPON");
        coupon.setCouponType("GENERAL");
        coupon.setCouponName("单元测试用券");
        coupon.setStatus(CouponStatusEnum.UNUSED);
        coupon.setSourceType("MANUAL");
        coupon.setSourceBizId(sourceBizId);
        coupon.setValidStartTime(LocalDateTime.now());
        coupon.setValidEndTime(LocalDateTime.now().plusDays(30));
        coupon.setDiscountValue(new BigDecimal("20.00"));
        return coupon;
    }
}
