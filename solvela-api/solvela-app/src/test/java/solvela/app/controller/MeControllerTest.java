package solvela.app.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.app.auth.AsMember;
import solvela.app.auth.MemberPrincipal;
import solvela.app.service.UnreadCounter;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.MallApi;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.DeliveryApi;
import solvela.member.api.DeliverySummaryView;
import solvela.member.api.MemberGradeApi;
import solvela.member.api.MemberGradeView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「我的」页汇总。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>每个数都用登录态里的 memberId 去问，不接受客户端传；</li>
 *   <li>🔴 一个下游挂了只让那一个数变 null，整块照样返回 —— 前端对 null 显示「—」，入口还在。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeControllerTest {

    private static final long MEMBER_ID = 4611884295L;
    private static final MemberPrincipal MEMBER = new MemberPrincipal(MEMBER_ID, "m", "n", null, null);

    @Mock
    private UnreadCounter unreadCounter;
    @Mock
    private MemberGradeApi memberGradeApi;
    @Mock
    private CouponQueryApi couponQueryApi;
    @Mock
    private ActivityApi activityApi;
    @Mock
    private DeliveryApi deliveryApi;
    @Mock
    private MallApi mallApi;

    @InjectMocks
    private MeController controller;

    @BeforeEach
    void setUp() {
        MemberGradeView grade = mock(MemberGradeView.class);
        when(grade.gradeName()).thenReturn("普通会员");
        when(memberGradeApi.myGrade(MEMBER_ID)).thenReturn(grade);
        when(unreadCounter.count(MEMBER_ID)).thenReturn(2L);
        when(couponQueryApi.countUsable(MEMBER_ID)).thenReturn(3L);
        when(activityApi.countMyLotteryTickets(MEMBER_ID)).thenReturn(120L);
        when(mallApi.countFavorites(MEMBER_ID)).thenReturn(1L);
        when(deliveryApi.summary(MEMBER_ID)).thenReturn(new DeliverySummaryView(5, 1, 2, 1));
    }

    @Test
    @DisplayName("一次给齐：每个数都按登录态的 memberId 去问")
    void 一次给齐() throws Exception {
        MeController.MeSummaryView view = AsMember.call(MEMBER, controller::summary);

        assertEquals(2L, view.unread());
        assertEquals("普通会员", view.gradeName());
        assertEquals(3L, view.coupons());
        // 超过列表上限（100）的数也照实给 —— 这正是不让前端拿列表去数的原因
        assertEquals(120L, view.lotteryTickets());
        assertEquals(1L, view.favorites());
        assertEquals(new DeliverySummaryView(5, 1, 2, 1), view.deliveries());
        verify(couponQueryApi).countUsable(MEMBER_ID);
    }

    @Test
    @DisplayName("🔴 一个下游挂了，只让那一个数变 null，整块照样返回")
    void 部分失败() throws Exception {
        when(activityApi.countMyLotteryTickets(MEMBER_ID)).thenThrow(new RuntimeException("彩票那边挂了"));
        when(memberGradeApi.myGrade(MEMBER_ID)).thenThrow(new RuntimeException("等级那边挂了"));

        MeController.MeSummaryView view = AsMember.call(MEMBER, controller::summary);

        assertNull(view.lotteryTickets());
        assertNull(view.gradeName());
        // 其余照常
        assertEquals(3L, view.coupons());
        assertEquals(2L, view.unread());
    }
}
