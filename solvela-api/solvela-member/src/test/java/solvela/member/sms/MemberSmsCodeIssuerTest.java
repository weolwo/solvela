package solvela.member.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.crypto.PiiHasher;
import solvela.member.api.SmsCodeFailReason;
import solvela.member.api.SmsCodeSendResult;
import solvela.member.api.SmsDelivery;
import solvela.member.api.SmsScene;
import solvela.member.register.MemberRegisterDao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 这条短信<b>到底发不发</b>的那张判定表。
 *
 * <h3>🔴 为什么这张表值得单独钉住</h3>
 * 短信是<b>真的要花钱、真的会响在陌生人手机上</b>的。判错一格的后果不是报错，是：
 * <ul>
 *   <li>该拦没拦 → 攻击者拿一份号码库就能把它刷成实打实的账单，
 *       而受害者只是莫名其妙收到一堆验证码；</li>
 *   <li>不该拦拦了 → 用户永远收不到码，而接口<b>返回的是成功</b>，
 *       客服和开发都看不出问题在哪。</li>
 * </ul>
 *
 * <h3>返回值必须一模一样</h3>
 * 发与不发，调用方拿到的都是「成功」。回答不一样的话，
 * 这个接口就成了「这个号注册过没有」的查询器。
 *
 * @Date 2026-09-10
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberSmsCodeIssuerTest {

    private static final String PHONE = "13800138000";

    private static final String HASH = "abcdef0123456789";

    private static final String IP = "10.0.0.1";

    private static final Long MEMBER_ID = 900_001L;

    @Mock
    private MemberSmsCodeService smsCodeService;
    @Mock
    private MemberRegisterDao memberRegisterDao;
    @Mock
    private PiiHasher piiHasher;

    private MemberSmsCodeIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new MemberSmsCodeIssuer(smsCodeService, memberRegisterDao, piiHasher);
        when(piiHasher.hash(anyString())).thenReturn(HASH);
        when(smsCodeService.send(any(), anyString(), any(), any()))
                .thenReturn(SmsCodeSendResult.ok());
    }

    /** 捕获真正传给发送服务的那个投递决定。 */
    private SmsDelivery deliveryOf(SmsScene scene, Long currentMemberId) {
        issuer.issue(scene, PHONE, IP, currentMemberId);
        ArgumentCaptor<SmsDelivery> captor = ArgumentCaptor.forClass(SmsDelivery.class);
        verify(smsCodeService).send(eq(scene), eq(PHONE), eq(IP), captor.capture());
        return captor.getValue();
    }

    private void phoneRegistered(boolean registered) {
        when(memberRegisterDao.countByPhoneHash(HASH)).thenReturn(registered ? 1 : 0);
    }

    private void occupiedByOthers(boolean occupied) {
        when(memberRegisterDao.countByPhoneHashExcludingMember(eq(HASH), any()))
                .thenReturn(occupied ? 1 : 0);
    }

    // ============================== 注册 ==============================

    @Test
    @DisplayName("注册：没注册过 → 发")
    void 注册新号码() {
        phoneRegistered(false);

        assertEquals(SmsDelivery.DELIVER, deliveryOf(SmsScene.REGISTER, null));
    }

    @Test
    @DisplayName("注册：已注册过 → 不发。那句「已注册」藏不掉，但没必要再花一条短信去说")
    void 注册已有号码() {
        phoneRegistered(true);

        assertEquals(SmsDelivery.SUPPRESS, deliveryOf(SmsScene.REGISTER, null));
    }

    // ============================== 登录 / 重置 ==============================

    @Test
    @DisplayName("🔴 重置密码：没注册过 → 不发。给陌生号码发重置码是纯浪费，也是骚扰")
    void 重置陌生号码() {
        phoneRegistered(false);

        assertEquals(SmsDelivery.SUPPRESS, deliveryOf(SmsScene.RESET_PASSWORD, null));
    }

    @Test
    @DisplayName("重置密码：注册过 → 发")
    void 重置已有号码() {
        phoneRegistered(true);

        assertEquals(SmsDelivery.DELIVER, deliveryOf(SmsScene.RESET_PASSWORD, null));
    }

    @Test
    @DisplayName("登录：与重置同一档 —— 没有账号就没有可登录的东西")
    void 登录陌生号码() {
        phoneRegistered(false);

        assertEquals(SmsDelivery.SUPPRESS, deliveryOf(SmsScene.LOGIN, null));
    }

    // ============================== 绑定 ==============================

    @Test
    @DisplayName("🔴 绑定看的是「被【别人】占了没有」，不是「有没有人占」")
    void 绑定自己的旧号码() {
        // 这个号确实被占着 —— 被他自己占着
        phoneRegistered(true);
        occupiedByOthers(false);

        assertEquals(SmsDelivery.DELIVER, deliveryOf(SmsScene.BIND, MEMBER_ID),
                "按 exists 判的话，会员给【自己的旧号码】发码永远收不到 —— "
                        + "而那条路正是没设过密码的会员唯一能换绑的方式");
    }

    @Test
    @DisplayName("绑定：号码被别人占着 → 不发")
    void 绑定别人的号码() {
        phoneRegistered(true);
        occupiedByOthers(true);

        assertEquals(SmsDelivery.SUPPRESS, deliveryOf(SmsScene.BIND, MEMBER_ID));
    }

    // ============================== 共同性质 ==============================

    @Test
    @DisplayName("🔴 发与不发，返回给调用方的结果【一模一样】")
    void 两种投递的返回值相同() {
        phoneRegistered(false);
        SmsCodeSendResult suppressed = issuer.issue(SmsScene.RESET_PASSWORD, PHONE, IP, null);

        phoneRegistered(true);
        SmsCodeSendResult delivered = issuer.issue(SmsScene.RESET_PASSWORD, PHONE, IP, null);

        assertEquals(delivered.success(), suppressed.success(),
                "回答不一样的话，这个接口就成了「这个号注册过没有」的查询器");
        assertEquals(delivered.reason(), suppressed.reason());
    }

    @Test
    @DisplayName("号码格式不对 → 直接拒，不查库也不进发送流程")
    void 格式不对() {
        assertEquals(SmsCodeFailReason.BAD_PHONE_FORMAT,
                issuer.issue(SmsScene.REGISTER, "not-a-phone", IP, null).reason());

        verify(smsCodeService, never()).send(any(), anyString(), any(), any());
        verify(memberRegisterDao, never()).countByPhoneHash(anyString());
    }

    @Test
    @DisplayName("传给发送服务的是【规范化后】的号码 —— 验码那一侧也用它，两边必须同一个键")
    void 传规范化后的号码() {
        phoneRegistered(false);

        issuer.issue(SmsScene.REGISTER, "+86 138 0013 8000", IP, null);

        verify(smsCodeService).send(eq(SmsScene.REGISTER), eq(PHONE), eq(IP), any());
    }
}
