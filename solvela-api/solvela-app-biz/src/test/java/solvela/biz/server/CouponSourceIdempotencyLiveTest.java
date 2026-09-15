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
 * 发券的<b>防重唯一键</b>（{@code uk_source}）是不是真的在。
 *
 * <h3>为什么这几条必须打在真库上</h3>
 * {@code CouponManualGrantServiceTest} 里的「重复提交算跳过」是靠 mock 抛
 * {@code DuplicateKeyException} 演出来的 —— 那证明的是<b>接住异常之后的处理对</b>，
 * 证明不了<b>异常真的会被抛出来</b>。
 *
 * <p>而后者恰恰是最容易悄悄失效的一环：索引被谁删了、新环境忘了执行那个脚本，
 * 代码这一侧一个字都不用改就能跑 —— 表现是运营双击一次多发一批券，而且没有任何报错。
 *
 * <h3>🔴 {@code CouponAssetHandler} 的那段「防重拦截」曾经是<b>死代码</b></h3>
 * 它 catch 的 {@code DuplicateKeyException} 在 2026-09-15 之前<b>永远不会被抛出</b>——
 * 表上只有普通索引 {@code idx_source}，没有任何唯一约束可违反。
 * 那段 catch 从写下的第一天起就没执行过，而没人会发现：
 * 重发一个提案就多一张券，不报错、不告警。
 *
 * <p>所以 {@link #发奖来源重复也会被挡住()} 是这个类里最重要的一条 ——
 * 它盯的不是「处理得对不对」，而是「那条路还通不通」。
 *
 * <p>⚠️ {@code @Transactional} 回滚：本测试真往券表里写行。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@SpringBootTest
@Transactional
class CouponSourceIdempotencyLiveTest {

    private static final Long TEST_MEMBER_ID = -90002L;
    private static final String TICKET = "ZZTEST-TICKET-001";

    @Autowired
    private MemberCouponDao memberCouponDao;

    @Test
    @DisplayName("🔴 同一个工单对同一个人重复发 → 被数据库挡住（uk_source 真的在）")
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
    @DisplayName("🔴 同一个提案重复发券 → 也被挡住。这一条 2026-09-15 之前是【红的反面】")
    void 发奖来源重复也会被挡住() {
        MemberCoupon first = manualCoupon("ZZTEST-PROPOSAL-1");
        first.setSourceType("PROPOSAL");
        MemberCoupon second = manualCoupon("ZZTEST-PROPOSAL-1");
        second.setSourceType("PROPOSAL");

        memberCouponDao.insert(first);

        /*
         * 挡不住的话，CouponAssetHandler 里那段 catch DuplicateKeyException
         * 就又变回死代码 —— 重发一个提案多一张券，不报错、不告警。
         *
         * ⚠️ 这条曾经断言的是【相反】的事（「非 MANUAL 的重复照旧允许」），
         *    因为当时库里有 53 组 PROPOSAL 重复，全表唯一键建不出来。
         *    那 53 组查下来是【提案 id 被两代造数复用】—— 两行属于不同会员，
         *    不是同一个提案发了两张券。清掉之后这一条就翻过来了。
         */
        assertThrows(DuplicateKeyException.class, () -> memberCouponDao.insert(second));
    }

    @Test
    @DisplayName("不同来源类型用同一个单号不冲突 —— 唯一键是 (source_type, source_biz_id) 两列")
    void 不同来源类型不互相干扰() {
        MemberCoupon proposal = manualCoupon("ZZTEST-SAME-ID");
        proposal.setSourceType("PROPOSAL");
        MemberCoupon reissue = manualCoupon("ZZTEST-SAME-ID");
        reissue.setSourceType("REISSUE");

        // 提案 id 和旧券 id 是两套独立的号，撞上纯属巧合，不该互相挡
        assertAll(
                () -> assertDoesNotThrow(() -> memberCouponDao.insert(proposal)),
                () -> assertDoesNotThrow(() -> memberCouponDao.insert(reissue)));
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
