package solvela.member.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.LoginLogResultEnum;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskOutcomeEnum;
import solvela.enums.MemberStatusEnum;
import solvela.enums.MemberVerifyChannelEnum;
import solvela.member.Member;
import solvela.member.MemberLoginLog;
import solvela.member.MemberRiskEvent;
import solvela.member.api.AuthFailReason;
import solvela.member.api.EmailCodeVerifyResult;
import solvela.member.api.LoginChallengeCmd;
import solvela.member.api.LoginChallengeCodeResult;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberLoginType;
import solvela.member.api.SmsCodeSendResult;
import solvela.member.api.SmsCodeVerifyResult;
import solvela.member.api.SmsScene;
import solvela.member.device.DeviceGuard;
import solvela.member.email.MemberEmailCodeService;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.member.password.LeakedPasswordNotifier;
import solvela.member.register.MemberRegisterService;
import solvela.member.risk.MemberRiskEngine;
import solvela.member.risk.MemberRiskProperties;
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskActionExecutor;
import solvela.member.risk.RiskDecision;
import solvela.member.risk.RiskEventRecorder;
import solvela.member.risk.RiskEventRef;
import solvela.member.risk.RiskSession;
import solvela.member.risk.VerifyRouter;
import solvela.member.risk.feature.AccountFailTracker;
import solvela.member.risk.feature.LoginProfileProvider;
import solvela.member.risk.feature.PasswordCooldownStore;
import solvela.member.sms.MemberSmsCodeService;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员认证：<b>分支顺序本身就是安全设计</b>。
 *
 * <h3>这里钉的几件事</h3>
 * <ul>
 *   <li><b>状态在验密码之前</b>。反过来的话，被冻结的账号还能拿来试探密码对不对 ——
 *       一个已经被判定为高风险的账号，不该再提供任何「密码猜对了没有」的信号；</li>
 *   <li><b>前置风控在验密码之前</b>。放到后面，被冷却期间每次尝试仍然会走一遍密码比对，
 *       冷却就只剩一句提示语，拦不住任何东西；</li>
 *   <li><b>查无此人要和「有这个号」长得一样</b> —— 包括<b>耗时</b>：不跑一次 Argon2 的话，
 *       响应时间本身就在回答「这个号存不存在」；</li>
 *   <li><b>失败要记数、成功要清数</b>。少了清零那一步，用户今天错两次、下周再错三次
 *       就被冷却 —— 而他两次都成功登录过；</li>
 *   <li><b>每一次尝试都有一行风控流水</b>，连格式错和异常路径也有；</li>
 *   <li><b>写登录日志失败绝不能影响登录</b>。</li>
 * </ul>
 *
 * <h3>风控用真会话、假引擎</h3>
 * 引擎自己的行为（规则怎么跑、结论怎么算）由 {@code MemberRiskEngineTest} 覆盖；
 * 这里只管「主干拿到某个结论之后做对了什么」，所以结论直接给，而 {@link RiskSession} 用<b>真的</b>——
 * 落库、结局记录都要真的发生，才能钉住「哪条路径漏了流水」。
 *
 * @Author alaric
 * @Date 2026-09-06
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberAuthServiceTest {

    private static final Long MEMBER_ID = 900001L;
    private static final String PHONE = "13800000000";
    private static final String PHONE_HASH = "ABCDEF";
    private static final String RAW_PASSWORD = "Passw0rd!";
    private static final String DEVICE_ID = "0123456789abcdef0123456789abcdef";

    @Mock
    private MemberAuthDao memberAuthDao;
    @Mock
    private MemberRegisterService memberRegisterService;
    @Mock
    private MemberLoginLogDao memberLoginLogDao;
    @Mock
    private PiiHasher piiHasher;
    @Mock
    private DeviceGuard deviceGuard;
    @Mock
    private LoginIpGuard loginIpGuard;
    @Mock
    private LoginChallengeStore challengeStore;
    @Mock
    private NewDeviceLoginNotifier newDeviceLoginNotifier;
    /**
     * 「你正在用的密码已泄露」那条提醒（P3 ③ 的后半截）。
     *
     * <p>本类不断言它 —— 发不发由 {@code LeakedPasswordNotifierTest} 钉。
     * 这里只是让它别是 null：它挂在验密码那一行之后，而<b>登录不该因为它而炸</b>。
     */
    @Mock
    private LeakedPasswordNotifier leakedPasswordNotifier;
    /** 动作怎么执行由 {@code RiskActionExecutorTest} 钉；这里只钉「生效结论的动作交给了它」 */
    @Mock
    private RiskActionExecutor riskActions;
    @Mock
    private MemberSmsCodeService smsCodeService;
    @Mock
    private MemberEmailCodeService emailCodeService;
    @Mock
    private MemberRiskEngine riskEngine;
    @Mock
    private PasswordCooldownStore cooldownStore;
    @Mock
    private AccountFailTracker failTracker;
    @Mock
    private LoginProfileProvider profileProvider;
    @Mock
    private VerifyRouter verifyRouter;
    @Mock
    private PiiCipher piiCipher;

    /** 真会话要往它落库。顺手也成了「这条路径有没有写流水」的探针 */
    @Mock
    private RiskEventRecorder recorder;

    private final MemberRiskProperties riskProperties = new MemberRiskProperties();

    @InjectMocks
    private MemberAuthService service;

    private Member member;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setMemberId(MEMBER_ID);
        member.setMemberName("sv900001");
        member.setNickname("小明");
        member.setStatus(MemberStatusEnum.NORMAL);
        member.setPassword(PasswordCipher.encode(RAW_PASSWORD));
        // 发码 / 验码按会员号现取联系方式（换通道时票上的 identity 不是那个东西），所以这里要有
        member.setPhone(PHONE);

        when(piiHasher.hash(PHONE)).thenReturn(PHONE_HASH);
        when(piiCipher.decrypt(PHONE)).thenReturn(PHONE);
        when(memberAuthDao.selectForAuth(MEMBER_ID)).thenReturn(member);
        // 发码 / 验码的目标单独查一次（登录那个查询刻意不取手机号、邮箱）
        when(memberAuthDao.selectContacts(MEMBER_ID)).thenReturn(member);
        // 核身路由：默认走短信（与这批用例的手机号登录一致）。要验「一条通道都没有」的用例自己改
        when(verifyRouter.route(any(), any(), any()))
                .thenReturn(new VerifyRouter.Route(
                        MemberVerifyChannelEnum.SMS, false));
        when(memberAuthDao.selectForLogin(PHONE_HASH)).thenReturn(member);

        // 假引擎 + 真会话：结论由用例指定，落库照真发生
        when(riskEngine.open(any())).thenAnswer(i ->
                new RiskSession(i.getArgument(0), riskProperties, recorder, null));
        when(riskEngine.open(any(), any())).thenAnswer(i ->
                new RiskSession(i.getArgument(0), riskProperties, recorder, i.getArgument(1)));
        pre(RiskDecision.PASS);
        post(RiskDecision.PASS);
    }

    // ------------------------------------------------------------------ 正常路径

    @Test
    @DisplayName("认证通过：返回身份、清掉冷却计数、记一条成功日志和一行流水")
    void 认证通过() {
        MemberAuthResult result = service.authenticate(cmd(RAW_PASSWORD));

        assertTrue(result.success());
        assertEquals(MEMBER_ID, result.identity().memberId());
        assertEquals("小明", result.identity().nickname());
        // 少了这一步，用户今天错两次、下周再错三次就被冷却，而他两次都登录成功过
        verify(cooldownStore).onIdentityVerified(PHONE_HASH, DEVICE_ID);
        assertEquals(LoginLogResultEnum.LOGIN_SUCCESS, savedLog().getStatus());
        assertEquals(MemberRiskOutcomeEnum.SUCCESS, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("🔴 手机号先规范化再算摘要：带空格和带 +86 的写法要落到同一个 hash")
    void 手机号规范化后再算摘要() {
        service.authenticate(cmd("138 0000 0000", RAW_PASSWORD));
        service.authenticate(cmd("+8613800000000", RAW_PASSWORD));

        // 顺序错了的表现是「同一个号有时能登有时不能」，取决于用户这次有没有手滑打空格
        verify(piiHasher, never()).hash("138 0000 0000");
        verify(piiHasher, never()).hash("+8613800000000");
    }

    // ------------------------------------------------------------------ 顺序即安全

    @Test
    @DisplayName("🔴 冻结账号不进密码比对：不给「密码猜对了没有」这个信号")
    void 冻结账号不验密码() {
        member.setStatus(MemberStatusEnum.FROZEN);

        MemberAuthResult result = service.authenticate(cmd("随便一个错密码"));

        assertEquals(AuthFailReason.ACCOUNT_FROZEN, result.reason());
        // 没走到密码分支，所以不该记冷却计数
        verify(cooldownStore, never()).onPasswordFailure(any());
        assertEquals("账号已冻结", savedLog().getRemark());
        assertEquals(MemberRiskOutcomeEnum.FROZEN, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("🔴 密码冷却期间直接挡回：连试的机会都不给，否则冷却形同虚设")
    void 冷却期间不验密码() {
        pre(new RiskDecision(MemberRiskDecisionEnum.COOLDOWN, 275L, Set.of()));

        MemberAuthResult result = service.authenticate(cmd("随便一个错密码"));

        // 🔴 单独一个原因，不并进 OPERATION_LIMITED：被冷却的人还有验证码登录和找回密码两条路，
        //    而「操作过于频繁，请稍后再试」除了等什么也没告诉他
        assertEquals(AuthFailReason.PASSWORD_COOLDOWN, result.reason());
        assertEquals(275L, result.lockedSeconds(),
                "要告诉用户还得等多久，含糊其辞只会把人逼去打客服电话");
        verify(cooldownStore, never()).onPasswordFailure(any());
        assertEquals(MemberRiskOutcomeEnum.COOLDOWN, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("检查顺序：前置风控 → 状态 → 密码，一步都不能提前")
    void 分支顺序() {
        service.authenticate(cmd(RAW_PASSWORD));

        InOrder order = inOrder(riskEngine, memberAuthDao, cooldownStore);
        // 查会员挪到了风控之前（前置允许看账号，但只能把结论往松里调）
        order.verify(memberAuthDao).selectForLogin(anyString());
        order.verify(riskEngine).evaluatePre(any());
        order.verify(riskEngine).evaluatePost(any());
        order.verify(cooldownStore).onIdentityVerified(anyString(), any());
    }

    // ------------------------------------------------------------------ 失败

    @Test
    @DisplayName("手机号格式不合法：不查库、不写登录日志，但<b>要</b>留一行流水")
    void 手机号格式不合法() {
        MemberAuthResult result = service.authenticate(cmd("1380000", RAW_PASSWORD));

        assertEquals(AuthFailReason.BAD_PHONE_FORMAT, result.reason());
        verify(memberAuthDao, never()).selectForLogin(anyString());
        verify(memberLoginLogDao, never()).insert(any(MemberLoginLog.class));
        MemberRiskEvent event = savedEvent();
        assertEquals(MemberRiskOutcomeEnum.BAD_FORMAT, event.getOutcome());
        assertNull(event.getIdentityHashHex(), "格式都不对，谈不上是对哪个账号的尝试");
    }

    @Test
    @DisplayName("🔴 查无此人：回答与「有这个号」一模一样，而且<b>照样跑一次密码哈希</b>")
    void 手机号不存在() {
        when(memberAuthDao.selectForLogin(PHONE_HASH)).thenReturn(null);

        MemberAuthResult result = service.authenticate(cmd(RAW_PASSWORD));

        // 与「密码错」给同一个原因：区分开来等于送出一个手机号枚举接口
        assertEquals(AuthFailReason.BAD_CREDENTIALS, result.reason());
        verify(memberLoginLogDao, never()).insert(any(MemberLoginLog.class));
        // 🔴 不记冷却计数的话，「错几次会不会被冷却」又成了一个账号枚举接口
        verify(cooldownStore).onPasswordFailure(any());
        MemberRiskEvent event = savedEvent();
        assertEquals(MemberRiskOutcomeEnum.UNKNOWN_ACCOUNT, event.getOutcome());
        assertNull(event.getMemberId());
        assertEquals(PHONE_HASH, event.getIdentityHashHex(), "查无此人靠身份摘要聚合");
    }

    @Test
    @DisplayName("已注销：与查无此人给同一个原因")
    void 已注销() {
        member.setStatus(MemberStatusEnum.CANCELLED);

        assertEquals(AuthFailReason.BAD_CREDENTIALS, service.authenticate(cmd(RAW_PASSWORD)).reason());
    }

    @Test
    @DisplayName("没设过密码：单独一个原因，别混进「密码错误」")
    void 未设置密码() {
        member.setPassword(null);

        MemberAuthResult result = service.authenticate(cmd(RAW_PASSWORD));

        // 混进「密码错误」的话，用户会一直重试一个他从来没设过的密码
        assertEquals(AuthFailReason.NO_PASSWORD, result.reason());
        verify(cooldownStore, never()).onPasswordFailure(any());
    }

    @Test
    @DisplayName("密码错误：记一次冷却计数、写失败日志，返回「凭证错误」")
    void 密码错误() {
        MemberAuthResult result = service.authenticate(cmd("WrongPass1!"));

        assertEquals(AuthFailReason.BAD_CREDENTIALS, result.reason());
        verify(cooldownStore).onPasswordFailure(any());
        verify(cooldownStore, never()).onIdentityVerified(anyString(), any());
        assertEquals(LoginLogResultEnum.LOGIN_FAIL, savedLog().getStatus());
        assertEquals(MemberRiskOutcomeEnum.BAD_CREDENTIALS, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("🔴 这一次失败刚好触发冷却时，<b>不</b>当场把剩余时间说出来")
    void 失败触发冷却不当场告知() {
        MemberAuthResult result = service.authenticate(cmd("WrongPass1!"));

        /*
         * 改造前会立刻返回「还要等多久」（理由是别让用户再点一次才发现）。
         * 而冷却现在按身份摘要计数、对不存在的账号同样触发 —— 这一刻说出来就等于
         * 回答了「这个号存不存在」。代价是用户要再点一次才看到倒计时。
         */
        assertEquals(AuthFailReason.BAD_CREDENTIALS, result.reason());
        assertEquals(0L, result.lockedSeconds());
    }

    @Test
    @DisplayName("前置判要滑块而请求没带票 → CAPTCHA_REQUIRED，密码压根不验")
    void 要滑块() {
        pre(new RiskDecision(MemberRiskDecisionEnum.CAPTCHA, 0L, Set.of()));

        MemberAuthResult result = service.authenticate(cmd("随便一个错密码"));

        assertEquals(AuthFailReason.CAPTCHA_REQUIRED, result.reason());
        verify(cooldownStore, never()).onPasswordFailure(any());
        assertEquals(MemberRiskOutcomeEnum.CAPTCHA_REQUIRED, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("前置判要滑块但票已经核销过 → 照常往下走")
    void 滑块已过() {
        pre(new RiskDecision(MemberRiskDecisionEnum.CAPTCHA, 0L, Set.of()));

        MemberAuthResult result = service.authenticate(new MemberAuthCmd(MemberLoginType.PHONE_PASSWORD,
                PHONE, RAW_PASSWORD, null, "H5", "127.0.0.1", DEVICE_ID, true));

        assertTrue(result.success());
    }

    @Test
    @DisplayName("前置限流 / 拒绝：对外同一句，不验密码、不写登录日志")
    void 前置拦下() {
        pre(new RiskDecision(MemberRiskDecisionEnum.DENY, 600L, Set.of()));

        MemberAuthResult result = service.authenticate(cmd(RAW_PASSWORD));

        assertEquals(AuthFailReason.IP_LIMITED, result.reason(),
                "拒绝与限流共用一句文案 —— 告诉对方「你被拉黑了」等于给他调试反馈");
        assertEquals(600L, result.lockedSeconds());
        verify(memberLoginLogDao, never()).insert(any(MemberLoginLog.class));
        assertEquals(MemberRiskOutcomeEnum.PRE_BLOCKED, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("生效结论带的动作要执行：前置结论原样交给动作执行器")
    void 执行降档动作() {
        RiskDecision decision = new RiskDecision(MemberRiskDecisionEnum.LIMIT, 60L, Set.of(RiskAction.OBSERVE_DEVICE));
        pre(decision);

        service.authenticate(cmd(RAW_PASSWORD));

        verify(riskActions).execute(eq(decision), any());
    }

    // ------------------------------------------------------------------ 日志与流水

    @Test
    @DisplayName("🔴 写登录日志炸了，登录照样成功")
    void 日志失败不影响认证() {
        doThrow(new RuntimeException("t_member_login_log 写满了"))
                .when(memberLoginLogDao).insert(any(MemberLoginLog.class));

        MemberAuthResult result = service.authenticate(cmd(RAW_PASSWORD));

        // 「登不上去是因为日志表满了」排查极其费劲，而日志的价值再高也高不过登录本身
        assertTrue(result.success());
        assertNotNull(result.identity());
    }

    @Test
    @DisplayName("🔴 风控流水写炸了，登录照样成功")
    void 流水失败不影响认证() {
        when(recorder.record(any(), any())).thenThrow(new RuntimeException("库挂了"));

        assertTrue(service.authenticate(cmd(RAW_PASSWORD)).success());
    }

    @Test
    @DisplayName("🔴 主干抛异常时也有流水：结局记成「内部异常」")
    void 异常路径也有流水() {
        when(memberAuthDao.selectForLogin(PHONE_HASH)).thenReturn(member);
        doThrow(new RuntimeException("Redis 挂了")).when(newDeviceLoginNotifier)
                .onLoginSucceeded(any(), any(), any(), any());

        Assertions.assertThrows(RuntimeException.class,
                () -> service.authenticate(cmd(RAW_PASSWORD)));

        assertEquals(MemberRiskOutcomeEnum.INTERNAL_ERROR, savedEvent().getOutcome(),
                "登录报 500 时，风控判了什么、命中了哪几条规则，正是最想知道的");
    }

    @Test
    @DisplayName("设备类型缺省成 H5，客户端 IP 原样落库")
    void 日志字段缺省() {
        service.authenticate(MemberAuthCmd.byPhonePassword(PHONE, RAW_PASSWORD, null, "10.0.0.7", DEVICE_ID));

        MemberLoginLog log = savedLog();
        assertEquals("H5", log.getDeviceType());
        assertEquals("10.0.0.7", log.getClientIp());
        assertNull(log.getRemark(), "成功不需要备注");
    }

    // ------------------------------------------------------------------ 按会员号取身份

    @Test
    @DisplayName("getAuthIdentity：只有 NORMAL 才是可用身份，冻结/注销一律 null")
    void 取身份只认正常状态() {
        when(memberAuthDao.selectForAuth(MEMBER_ID)).thenReturn(member);
        assertNotNull(service.getAuthIdentity(MEMBER_ID));

        // 「什么算一个可用身份」只该有一个定义 —— 多一处判断就多一次判漏的机会，
        // 而判漏的表现是「被冻结的人还能正常用」，没有任何报错
        member.setStatus(MemberStatusEnum.FROZEN);
        assertNull(service.getAuthIdentity(MEMBER_ID));

        member.setStatus(MemberStatusEnum.CANCELLED);
        assertNull(service.getAuthIdentity(MEMBER_ID));

        assertNull(service.getAuthIdentity(null));
    }

    // ------------------------------------------------------------------ 二次验证

    @Test
    @DisplayName("🔴 后置判要 OTP + 密码对 → 签一张绑在这台设备上的凭票，票里带着流水引用")
    void 要二次验证() {
        post(new RiskDecision(MemberRiskDecisionEnum.OTP, 0L, Set.of()));
        when(recorder.record(any(), any())).thenReturn(4242L);
        when(challengeStore.issue(any())).thenReturn("lc_ticket");

        MemberAuthResult result = service.authenticate(cmd(PHONE, RAW_PASSWORD));

        assertFalse(result.success());
        assertEquals(AuthFailReason.DEVICE_VERIFICATION_REQUIRED, result.reason(),
                "密码是对的，只是还差一步。回成 BAD_CREDENTIALS 的话，"
                        + "用户会一直以为自己密码记错了，去走找回密码 —— 而那解决不了他的问题");
        assertEquals("lc_ticket", result.challengeTicket());
        ArgumentCaptor<LoginChallengeStore.Challenge> captor =
                ArgumentCaptor.forClass(LoginChallengeStore.Challenge.class);
        verify(challengeStore).issue(captor.capture());
        LoginChallengeStore.Challenge challenge = captor.getValue();
        assertEquals(MEMBER_ID, challenge.memberId());
        assertEquals(DEVICE_ID, challenge.deviceId());
        assertEquals(PHONE, challenge.identity());
        assertEquals(4242L, challenge.eventRef().eventId(),
                "🔴 票里要带签票那一行流水，验码那一行才能指回来");
        assertEquals(MemberRiskOutcomeEnum.CHALLENGED, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("🔴 邮箱验证码登录即使被判要 OTP 也直接放行：它本身就是一道码")
    void 邮箱码登录不再要第二道() {
        post(new RiskDecision(MemberRiskDecisionEnum.OTP, 0L, Set.of()));
        when(piiHasher.hash("a@x.com")).thenReturn("EMAILHASH");
        when(memberAuthDao.selectForLoginByEmail("EMAILHASH")).thenReturn(member);
        when(emailCodeService.verify(any(), eq("a@x.com"), eq("123456")))
                .thenReturn(EmailCodeVerifyResult.OK);

        MemberAuthResult result = service.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_CODE,
                "a@x.com", "123456", null, "H5", "127.0.0.1", DEVICE_ID, false));

        assertTrue(result.success(), "码已经验过了，不该再要第二道 —— 第二封信证明不了第一封证明不了的任何事情");
        verify(challengeStore, never()).issue(any());
    }

    @Test
    @DisplayName("🔴 二次验证排在密码校验【之后】—— 否则谁都能拿一个手机号让我们发短信")
    void 二次验证排在密码之后() {
        post(new RiskDecision(MemberRiskDecisionEnum.OTP, 0L, Set.of()));

        MemberAuthResult result = service.authenticate(
                new MemberAuthCmd(MemberLoginType.PHONE_PASSWORD, PHONE, "WrongPassword9", "123456",
                        "H5", "127.0.0.1", DEVICE_ID, false));

        assertEquals(AuthFailReason.BAD_CREDENTIALS, result.reason(),
                "密码错就该在密码那一关挡下，不该走到二次验证");
        verify(challengeStore, never()).issue(any());
        verify(smsCodeService, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("后置放行时完全不多一次往返 —— 绝大多数登录是这一条")
    void 后置放行不要二次验证() {
        assertTrue(service.authenticate(cmd(PHONE, RAW_PASSWORD)).success());

        verify(challengeStore, never()).issue(any());
        verify(smsCodeService, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("后置限流（一机多号这类）→ 密码验过了也不放行")
    void 后置拦下() {
        post(new RiskDecision(MemberRiskDecisionEnum.LIMIT, 3600L, Set.of()));

        MemberAuthResult result = service.authenticate(cmd(PHONE, RAW_PASSWORD));

        assertFalse(result.success());
        assertEquals(AuthFailReason.DEVICE_LIMITED, result.reason());
        assertEquals(MemberRiskOutcomeEnum.POST_BLOCKED, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("密码错时，冷却与设备失败计数【两边都要记】")
    void 密码错两边都记() {
        service.authenticate(cmd(PHONE, "WrongPassword9"));

        verify(cooldownStore).onPasswordFailure(any());
        verify(deviceGuard).recordLoginFailure(DEVICE_ID);
        // 🔴 第三个维度：失败是从哪个 IP 来的。冷却只数次数，数不到「多个来源围攻同一个号」
        verify(failTracker).record(eq(MEMBER_ID), eq("127.0.0.1"), any());
    }

    @Test
    @DisplayName("🔴 登录成功要让画像缓存失效：不然在新网络验过一次码，下次来还是「陌生网络」")
    void 成功后画像失效() {
        service.authenticate(cmd(RAW_PASSWORD));

        verify(profileProvider).evict(MEMBER_ID);
        verify(failTracker).clear(MEMBER_ID);
    }

    @Test
    @DisplayName("🔴 核身通道写进票里：发码、验码都只看它，不按登录方式再算一遍")
    void 通道写进票里() {
        post(new RiskDecision(MemberRiskDecisionEnum.OTP_STRONG, 0L, Set.of()));
        // 手机号登录，但路由要求换通道 → 码走邮箱
        when(verifyRouter.route(any(), any(), any()))
                .thenReturn(new VerifyRouter.Route(
                        MemberVerifyChannelEnum.EMAIL, false));
        when(recorder.record(any(), any())).thenReturn(4242L);
        when(challengeStore.issue(any())).thenReturn("lc_ticket");

        MemberAuthResult result = service.authenticate(cmd(PHONE, RAW_PASSWORD));

        assertEquals(MemberVerifyChannelEnum.EMAIL, result.verifyChannel(),
                "客户端要据它说「验证码已发到你的邮箱」—— 说错了用户会去翻另一个收件箱，然后以为我们没发");
        ArgumentCaptor<LoginChallengeStore.Challenge> captor =
                ArgumentCaptor.forClass(LoginChallengeStore.Challenge.class);
        verify(challengeStore).issue(captor.capture());
        assertEquals(MemberVerifyChannelEnum.EMAIL, captor.getValue().channel());
        assertEquals(MemberVerifyChannelEnum.EMAIL.getValue(),
                savedEvent().getVerifyChannel(),
                "落库了才数得出「有多少次本该换通道却退回了」");
    }

    @Test
    @DisplayName("🔴 一条通道都没有 → 当场 LOGIN_VERIFY_UNAVAILABLE，不签票")
    void 没有核身通道() {
        post(new RiskDecision(MemberRiskDecisionEnum.OTP, 0L, Set.of()));
        when(verifyRouter.route(any(), any(), any())).thenReturn(null);

        MemberAuthResult result = service.authenticate(cmd(PHONE, RAW_PASSWORD));

        assertEquals(AuthFailReason.LOGIN_VERIFY_UNAVAILABLE, result.reason());
        verify(challengeStore, never()).issue(any());
        assertEquals(MemberRiskOutcomeEnum.POST_BLOCKED, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("🔴 「再错几次会冷却」对【有号】和【没号】必须一模一样，否则它就是个账号枚举接口")
    void 剩余次数不区分有号没号() {
        when(cooldownStore.onPasswordFailure(any())).thenReturn(1);

        MemberAuthResult hasAccount = service.authenticate(cmd(PHONE, "错密码"));

        // 没号：selectForLogin 返回 null
        when(memberAuthDao.selectForLogin(PHONE_HASH)).thenReturn(null);
        MemberAuthResult noAccount = service.authenticate(cmd(PHONE, "错密码"));

        assertEquals(AuthFailReason.BAD_CREDENTIALS, hasAccount.reason());
        assertEquals(AuthFailReason.BAD_CREDENTIALS, noAccount.reason());
        assertEquals(hasAccount.attemptsBeforeCooldown(), noAccount.attemptsBeforeCooldown(),
                "只在有号时给这个数的话，「错两次看看有没有提示」就是一个精确的账号枚举接口");
        assertEquals(1, noAccount.attemptsBeforeCooldown());
    }

    // ------------------------------------------------------------------ 凭票发码 / 验码

    private static final String TICKET = "lc_ticket";

    private void validTicket() {
        when(challengeStore.find(TICKET)).thenReturn(new LoginChallengeStore.Challenge(
                MEMBER_ID, MemberLoginType.PHONE_PASSWORD, DEVICE_ID, "H5",
                new RiskEventRef(4242L, LocalDateTime.now()),
                MemberVerifyChannelEnum.SMS, PHONE));
    }

    private static LoginChallengeCmd ticketCmd(String code, String deviceId) {
        return new LoginChallengeCmd(TICKET, code, "127.0.0.1", deviceId);
    }

    @Test
    @DisplayName("凭票发码：发到票上记着的那个手机号，客户端说了不算")
    void 凭票发码() {
        validTicket();
        when(smsCodeService.send(eq(SmsScene.LOGIN), eq(PHONE), any())).thenReturn(SmsCodeSendResult.ok());

        LoginChallengeCodeResult result = service.sendChallengeCode(ticketCmd(null, DEVICE_ID));

        assertTrue(result.success());
        verify(smsCodeService).send(eq(SmsScene.LOGIN), eq(PHONE), any());
    }

    @Test
    @DisplayName("凭票验码 + 码对 → 登录成功，票作废，流水指回签票那一行")
    void 凭票码对() {
        validTicket();
        when(smsCodeService.verify(SmsScene.LOGIN, PHONE, "123456")).thenReturn(SmsCodeVerifyResult.OK);
        when(memberAuthDao.selectForAuth(MEMBER_ID)).thenReturn(member);

        MemberAuthResult result = service.verifyChallenge(ticketCmd("123456", DEVICE_ID));

        assertTrue(result.success());
        assertEquals(MEMBER_ID, result.identity().memberId());
        verify(challengeStore).discard(TICKET);
        MemberRiskEvent event = savedEvent();
        assertEquals(MemberRiskEventTypeEnum.LOGIN_VERIFY, event.getEventType());
        assertEquals(MemberRiskOutcomeEnum.VERIFY_PASSED, event.getOutcome());
        assertEquals(4242L, event.getRefEventId(), "「要了码的人后来验过没有」要顺着它查");
    }

    @Test
    @DisplayName("凭票验码 + 码不对 → DEVICE_VERIFICATION_FAILED，票还留着可以再试")
    void 凭票码不对() {
        validTicket();
        when(smsCodeService.verify(any(), any(), any())).thenReturn(SmsCodeVerifyResult.MISMATCH);

        assertEquals(AuthFailReason.DEVICE_VERIFICATION_FAILED,
                service.verifyChallenge(ticketCmd("000000", DEVICE_ID)).reason());
        verify(challengeStore, never()).discard(any());
        verify(loginIpGuard).recordFailure("127.0.0.1");
        assertEquals(MemberRiskOutcomeEnum.VERIFY_FAILED, savedEvent().getOutcome());
    }

    @Test
    @DisplayName("🔴 码被错到作废 → 票也作废（CHALLENGE_EXPIRED），不能拿同一张票无限次「重发再猜」")
    void 错太多票作废() {
        validTicket();
        when(smsCodeService.verify(any(), any(), any())).thenReturn(SmsCodeVerifyResult.TOO_MANY_ATTEMPTS);

        assertEquals(AuthFailReason.CHALLENGE_EXPIRED,
                service.verifyChallenge(ticketCmd("000000", DEVICE_ID)).reason());
        verify(challengeStore).discard(TICKET);
    }

    @Test
    @DisplayName("🔴 票拿到别的设备上用 → CHALLENGE_EXPIRED，连码都不验")
    void 换设备无效() {
        validTicket();

        assertEquals(AuthFailReason.CHALLENGE_EXPIRED,
                service.verifyChallenge(ticketCmd("123456", "ffffffffffffffffffffffffffffffff")).reason());
        assertFalse(service.sendChallengeCode(ticketCmd(null, "ffffffffffffffffffffffffffffffff")).success());
        verify(smsCodeService, never()).verify(any(), any(), any());
        verify(smsCodeService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("票不存在 / 过期 → CHALLENGE_EXPIRED")
    void 票无效() {
        assertEquals(AuthFailReason.CHALLENGE_EXPIRED,
                service.verifyChallenge(ticketCmd("123456", DEVICE_ID)).reason());
    }

    // ------------------------------------------------------------------ 辅助

    private void pre(RiskDecision decision) {
        when(riskEngine.evaluatePre(any())).thenReturn(decision);
    }

    private void post(RiskDecision decision) {
        when(riskEngine.evaluatePost(any())).thenReturn(decision);
    }

    private MemberAuthCmd cmd(String password) {
        return cmd(PHONE, password);
    }

    private MemberAuthCmd cmd(String phone, String password) {
        return MemberAuthCmd.byPhonePassword(phone, password, "H5", "127.0.0.1", DEVICE_ID);
    }

    private MemberLoginLog savedLog() {
        ArgumentCaptor<MemberLoginLog> captor = ArgumentCaptor.forClass(MemberLoginLog.class);
        verify(memberLoginLogDao).insert(captor.capture());
        return captor.getValue();
    }

    private MemberRiskEvent savedEvent() {
        ArgumentCaptor<MemberRiskEvent> captor = ArgumentCaptor.forClass(MemberRiskEvent.class);
        verify(recorder).record(captor.capture(), any());
        return captor.getValue();
    }
}
