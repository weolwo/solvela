package solvela.member.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberVerifyChannelEnum;
import solvela.member.Member;
import solvela.member.api.MemberLoginType;
import solvela.member.auth.MemberAuthDao;
import solvela.member.sms.MemberSmsCodeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 核身路由（方案 §3.7）。
 *
 * <h3>这里钉住的是几件事</h3>
 * <ul>
 *   <li>{@code OTP_STRONG} 真的换通道 —— 它存在的全部意义就是<b>避开登录时用的那个因子</b>；</li>
 *   <li>强通道不可用时<b>退回</b>而不是拒绝。拒绝的代价落在主人身上（他除了等什么也做不了），
 *       而退回之后攻击者仍然要有另一个账户才进得去；</li>
 *   <li>一条都没有时返回 null —— 由主干翻成 {@code LOGIN_VERIFY_UNAVAILABLE}，而且是在签票之前；</li>
 *   <li>🔴 联系方式<b>单独查一次</b>，不搭登录那个查询的车（那个刻意不取 PII）。</li>
 * </ul>
 *
 * @Date 2026-10-16
 */
class VerifyRouterTest {

    private static final Long MEMBER_ID = 900001L;

    private MemberAuthDao memberAuthDao;

    private MemberSmsCodeService smsCodeService;

    /** 默认都不在冷静期；专门验冷静期的那条用例自己改 */
    private solvela.member.contact.ContactCoolingStore coolingStore;

    private VerifyRouter router;

    private Member contacts;

    @BeforeEach
    void setUp() {
        memberAuthDao = mock(MemberAuthDao.class);
        smsCodeService = mock(MemberSmsCodeService.class);
        coolingStore = mock(solvela.member.contact.ContactCoolingStore.class);
        router = new VerifyRouter(memberAuthDao, smsCodeService, coolingStore);
        contacts = new Member();
        contacts.setMemberId(MEMBER_ID);
        contacts.setPhone("enc(13800000000)");
        contacts.setEmail("enc(a@x.com)");
        when(memberAuthDao.selectContacts(MEMBER_ID)).thenReturn(contacts);
        when(smsCodeService.deliverable()).thenReturn(true);
    }

    @Test
    @DisplayName("普通 OTP：走登录时用的那条通道")
    void 普通OTP走同一条() {
        assertEquals(MemberVerifyChannelEnum.SMS,
                route(MemberLoginType.PHONE_PASSWORD, MemberRiskDecisionEnum.OTP).channel());
        assertEquals(MemberVerifyChannelEnum.EMAIL,
                route(MemberLoginType.EMAIL_PASSWORD, MemberRiskDecisionEnum.OTP).channel());
    }

    @Test
    @DisplayName("🔴 OTP_STRONG 换通道：手机号登录发邮箱，邮箱登录发短信")
    void 强OTP换通道() {
        assertEquals(MemberVerifyChannelEnum.EMAIL,
                route(MemberLoginType.PHONE_PASSWORD, MemberRiskDecisionEnum.OTP_STRONG).channel());
        assertEquals(MemberVerifyChannelEnum.SMS,
                route(MemberLoginType.EMAIL_PASSWORD, MemberRiskDecisionEnum.OTP_STRONG).channel());
    }

    @Test
    @DisplayName("🔴 强通道不可用 → 退回登录时那条，并标记 fellBack（不是拒绝登录）")
    void 强通道不可用时退回() {
        contacts.setEmail(null);

        VerifyRouter.Route route = route(MemberLoginType.PHONE_PASSWORD,
                MemberRiskDecisionEnum.OTP_STRONG);

        assertEquals(MemberVerifyChannelEnum.SMS, route.channel());
        assertTrue(route.fellBack(),
                "这个标记是「本该换通道却没换成」的计数来源 —— 接上短信那天它应该归零");
    }

    @Test
    @DisplayName("短信还没接通 → 手机号登录的 OTP 也走邮箱。它算不上「换通道」，是唯一一条")
    void 短信不可用时走邮箱() {
        when(smsCodeService.deliverable()).thenReturn(false);

        VerifyRouter.Route route = route(MemberLoginType.PHONE_PASSWORD, MemberRiskDecisionEnum.OTP);

        assertEquals(MemberVerifyChannelEnum.EMAIL, route.channel());
        assertFalse(route.fellBack());
    }

    @Test
    @DisplayName("🔴 一条都没有 → null。今天唯一的组合：手机号账号 + 短信没接 + 没绑邮箱")
    void 一条都没有() {
        when(smsCodeService.deliverable()).thenReturn(false);
        contacts.setEmail(null);

        assertNull(route(MemberLoginType.PHONE_PASSWORD, MemberRiskDecisionEnum.OTP));
        assertNull(route(MemberLoginType.PHONE_PASSWORD, MemberRiskDecisionEnum.OTP_STRONG));
    }

    @Test
    @DisplayName("会员号为 null → 直接 null，连查都不查")
    void 没有会员号() {
        assertNull(router.route(MemberLoginType.PHONE_PASSWORD, null, MemberRiskDecisionEnum.OTP));
        verify(memberAuthDao, never()).selectContacts(null);
    }

    @Test
    @DisplayName("查不到这个会员（签票到发码之间被注销）→ null，不是随便挑一条通道")
    void 查不到会员() {
        when(memberAuthDao.selectContacts(MEMBER_ID)).thenReturn(null);

        assertNull(route(MemberLoginType.EMAIL_PASSWORD, MemberRiskDecisionEnum.OTP));
    }

    @Test
    @DisplayName("🔴 冷静期内的通道【当作没有】：新绑的邮箱还在冷静期 → 手机号登录的 OTP 退回短信")
    void 冷静期内的通道当作没有() {
        when(coolingStore.cooling(MEMBER_ID, MemberVerifyChannelEnum.EMAIL)).thenReturn(true);

        // 要求换通道（本该走邮箱），但邮箱在冷静期 → 退回登录时那条
        VerifyRouter.Route route = route(MemberLoginType.PHONE_PASSWORD,
                MemberRiskDecisionEnum.OTP_STRONG);

        assertEquals(MemberVerifyChannelEnum.SMS, route.channel());
        assertTrue(route.fellBack(),
                "当作「这条通道不可用」而不是单独报错，于是「还有另一条就走另一条」这套逻辑一行都不用改");
    }

    @Test
    @DisplayName("两条都在冷静期 → null（由主干翻成 LOGIN_VERIFY_UNAVAILABLE）")
    void 两条都在冷静期() {
        when(coolingStore.cooling(MEMBER_ID, MemberVerifyChannelEnum.EMAIL)).thenReturn(true);
        when(coolingStore.cooling(MEMBER_ID, MemberVerifyChannelEnum.SMS)).thenReturn(true);

        assertNull(route(MemberLoginType.PHONE_PASSWORD, MemberRiskDecisionEnum.OTP));
    }

    @Test
    @DisplayName("手机号没绑过时不走短信，哪怕短信通道本身是通的")
    void 没绑手机号不走短信() {
        contacts.setPhone(null);

        assertEquals(MemberVerifyChannelEnum.EMAIL,
                route(MemberLoginType.PHONE_PASSWORD, MemberRiskDecisionEnum.OTP).channel());
    }

    private VerifyRouter.Route route(MemberLoginType loginType, MemberRiskDecisionEnum decision) {
        return router.route(loginType, MEMBER_ID, decision);
    }
}
