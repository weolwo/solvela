package solvela.ledger.coupon.issue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import solvela.coupon.CouponTemplate;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;
import solvela.enums.CouponScopeTypeEnum;
import solvela.enums.CouponStatusEnum;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.template.service.CouponTemplateService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 发券时的<b>规则快照</b>。
 *
 * <h3>这里守的是一条资损线</h3>
 * 券必须带着<b>发它那一刻</b>的规则走。如果核销时回头读模板当前值，
 * 运营把「满100减20」改成「满200减20」之后，用户手里那张券就贬值了 ——
 * 而用户什么都不知道。所以下面那条「模板改版后老券不受影响」的用例，
 * 验的不是代码路径，是这个设计本身还在不在。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@ExtendWith(MockitoExtension.class)
class CouponIssueServiceTest {

    private static final String CODE = "SUMMER2026";

    @Mock
    private CouponTemplateService couponTemplateService;

    private CouponIssueService service;

    @BeforeEach
    void setUp() {
        service = new CouponIssueService(couponTemplateService);
    }

    @Test
    @DisplayName("🔴 有模板：8 个规则字段逐个快照进券行，核销之后再不用回头看模板")
    void 规则快照进券行() {
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(percentTemplate());

        MemberCoupon coupon = service.newCoupon(cmd("活动侧的展示名"));

        assertAll(
                () -> assertEquals(3, coupon.getTemplateVersion(), "排查时要靠它回答「当时是哪一版」"),
                () -> assertEquals(CouponDiscountTypeEnum.PERCENT, coupon.getDiscountType()),
                () -> assertEquals(0, new BigDecimal("20").compareTo(coupon.getDiscountValue())),
                () -> assertEquals(0, new BigDecimal("100").compareTo(coupon.getMinAmount())),
                () -> assertEquals(0, new BigDecimal("50").compareTo(coupon.getMaxDiscount())),
                () -> assertEquals(CouponDeductTargetEnum.CASH, coupon.getDeductTarget()),
                () -> assertEquals(CouponScopeTypeEnum.CATEGORY, coupon.getScopeType()),
                () -> assertEquals("[\"DIGITAL\"]", coupon.getScopeRefs()),
                () -> assertEquals(CouponStatusEnum.UNUSED, coupon.getStatus()));
    }

    @Test
    @DisplayName("🔴 模板改版之后，之前发出去的券一个字段都不变 —— 券贬值是资损")
    void 模板改版不影响已发出去的券() {
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(percentTemplate());
        MemberCoupon old = service.newCoupon(cmd(null));

        // 运营把门槛从 100 抬到 200、封顶从 50 砍到 10，并发布成新版本
        CouponTemplate v4 = percentTemplate();
        v4.setVersion(4);
        v4.setMinAmount(new BigDecimal("200"));
        v4.setMaxDiscount(new BigDecimal("10"));
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(v4);

        MemberCoupon fresh = service.newCoupon(cmd(null));

        assertAll(
                // 老券手里那份是【当时抄下来的】，模板怎么改都碰不到它
                () -> assertEquals(0, new BigDecimal("100").compareTo(old.getMinAmount())),
                () -> assertEquals(0, new BigDecimal("50").compareTo(old.getMaxDiscount())),
                () -> assertEquals(3, old.getTemplateVersion()),
                // 新发的才按新规则
                () -> assertEquals(0, new BigDecimal("200").compareTo(fresh.getMinAmount())),
                () -> assertEquals(4, fresh.getTemplateVersion()));
    }

    @Test
    @DisplayName("券名：模板名赢过活动侧的展示名 —— 名字和规则必须是同一个人配的")
    void 模板名优先() {
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(percentTemplate());

        // 叫「无门槛券」而 min_amount=100 的券，用户看着名字去用，被拦下来，然后找客服
        assertEquals("满100减20优惠券", service.newCoupon(cmd("无门槛券")).getCouponName());
    }

    @Test
    @DisplayName("有效期：发券后 N 天")
    void 相对有效期() {
        LocalDateTime before = LocalDateTime.now();
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(percentTemplate());

        MemberCoupon coupon = service.newCoupon(cmd(null));

        assertTrue(coupon.getValidEndTime().isAfter(before.plusDays(29)));
        assertTrue(coupon.getValidEndTime().isBefore(before.plusDays(31)));
    }

    @Test
    @DisplayName("有效期：固定失效时间（活动券，不管什么时候发的都一起过期）")
    void 固定有效期() {
        CouponTemplate fixed = percentTemplate();
        fixed.setValidDays(null);
        LocalDateTime end = LocalDateTime.now().plusDays(3).withNano(0);
        fixed.setValidEndTime(end);
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(fixed);

        assertEquals(end, service.newCoupon(cmd(null)).getValidEndTime());
    }

    @Test
    @DisplayName("⚠️ 固定失效时间已经过去：照发不拒发 —— 中了奖却什么都没拿到更难解释")
    void 已过期的固定有效期照发() {
        CouponTemplate expired = percentTemplate();
        expired.setValidDays(null);
        LocalDateTime past = LocalDateTime.now().minusDays(1).withNano(0);
        expired.setValidEndTime(past);
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(expired);

        MemberCoupon coupon = service.newCoupon(cmd(null));

        // 发出来的是一张券包里明确写着「已过期」的券，客服查得到、运营也能看出模板配错了
        assertEquals(past, coupon.getValidEndTime());
        assertEquals(CouponStatusEnum.UNUSED, coupon.getStatus());
    }

    @Test
    @DisplayName("🔴 没有模板：照发但规则列全空 —— 拒发会把在架商品变成兑换必失败")
    void 没有模板时降级() {
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(null);

        MemberCoupon coupon = service.newCoupon(cmd("华为音乐 音乐VIP（年卡）"));

        assertAll(
                // 这不是配错：兑换凭证类的券本来就没有「减多少」这回事
                () -> assertEquals("华为音乐 音乐VIP（年卡）", coupon.getCouponName()),
                () -> assertNull(coupon.getTemplateVersion()),
                () -> assertNull(coupon.getDiscountType()),
                /*
                 * 🔴 留空而不是填 0。填 0 会变成一张「无门槛减 0」的券 ——
                 * 看起来配好了，实际上试算时减不出钱，而且再也分不清
                 * 是没配还是真配成了 0。
                 */
                () -> assertNull(coupon.getDiscountValue()),
                () -> assertNull(coupon.getMinAmount()),
                () -> assertNull(coupon.getDeductTarget()),
                () -> assertEquals(CouponStatusEnum.UNUSED, coupon.getStatus()));
    }

    @Test
    @DisplayName("没有模板、调用方也给不出展示名：回退用券编码，不编一个名字")
    void 降级时券名回退用编码() {
        when(couponTemplateService.getLatestEnabled(anyString())).thenReturn(null);

        assertEquals(CODE, service.newCoupon(cmd("   ")).getCouponName());
    }

    private static CouponIssueCmd cmd(String fallbackName) {
        return new CouponIssueCmd(CODE, 1001L, "tester", "MALL", "M2026:1", fallbackName);
    }

    private static CouponTemplate percentTemplate() {
        CouponTemplate template = new CouponTemplate();
        template.setCouponCode(CODE);
        template.setVersion(3);
        template.setCouponName("满100减20优惠券");
        template.setDiscountType(CouponDiscountTypeEnum.PERCENT);
        template.setDiscountValue(new BigDecimal("20"));
        template.setMinAmount(new BigDecimal("100"));
        template.setMaxDiscount(new BigDecimal("50"));
        template.setDeductTarget(CouponDeductTargetEnum.CASH);
        template.setScopeType(CouponScopeTypeEnum.CATEGORY);
        template.setScopeRefs("[\"DIGITAL\"]");
        template.setValidDays(30);
        template.setStatus(1);
        return template;
    }
}
