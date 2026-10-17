package solvela.member.register;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;
import solvela.base.event.BizEventPublisher;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskOutcomeEnum;
import solvela.member.MemberRiskEvent;
import solvela.member.auth.NewDeviceLoginNotifier;
import solvela.member.device.DeviceDispositionService;
import solvela.member.operationlimit.service.MemberOperationLimitService;
import solvela.member.risk.RiskActionExecutor;
import solvela.member.risk.SuspiciousLoginNotifier;
import solvela.member.risk.security.CanaryTrap;
import solvela.member.email.MemberEmailCodeService;
import solvela.member.sms.MemberSmsCodeService;
import solvela.member.api.MemberLoginType;
import solvela.member.api.SmsCodeVerifyResult;
import solvela.member.api.SmsScene;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.RegisterFailReason;
import solvela.member.id.MemberIdAllocator;
import solvela.member.password.WeakPasswordChecker;
import solvela.member.risk.MemberRiskEngine;
import solvela.member.risk.MemberRiskProperties;
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskDecision;
import solvela.member.risk.RiskEventRecorder;
import solvela.member.risk.RiskSession;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员注册：<b>顺序决定了能不能拿它当手机号探测器</b>。
 *
 * <h3>为什么限频必须排在查重前面</h3>
 * 反过来的话，「这个号注册过没有」可以无限次免费提问 —— {@code PHONE_TAKEN}
 * 这个枚举本身就是答案，它藏不掉。把限频提到查重之前，探测的成本才真的存在。
 *
 * <p>而格式与强度校验又排在限频<b>之前</b>：它们不查库、不泄露任何信息，
 * 让一个手滑打错格式的用户去消耗限频额度没有道理。
 *
 * <h3>限频 = 风控引擎的前置决策（2026-10-16）</h3>
 * 原来这里断言的是 {@code redisService.increment} 与 {@code deviceGuard.checkRegister}。
 * 那两道闸现在是四条规则，所以这些用例改成断言<b>引擎的位置与结论的落点</b>：
 * 「超了几次就该限流」属于规则自己的单测（{@code RegisterRuleTest}），
 * 不该在主干单测里再钉一遍 —— 那只会让同一件事有两处要改。
 *
 * <p>引擎是假的、{@link RiskSession} 是<b>真的</b>（与 {@code MemberAuthServiceTest} 同一个做法）：
 * 结论由用例指定，而「这条路径有没有写流水、写的是哪个结局」照真发生。
 *
 * <h3>撞唯一约束是预期结果，不是意外</h3>
 * 查重和插入之间有窗口，两个请求同时注册同一个号必然有一个撞唯一键。
 * 把它当异常抛出去就是 500 —— 而对用户这明明就是「已被注册」。
 *
 * <h3>密文与摘要必须来自同一个规范化后的字符串</h3>
 * 否则「解密出来的号」和「能登录的号」会是两个东西，而且要等到用户登不上才发现。
 *
 * @Author alaric
 * @Date 2026-09-06
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberRegisterServiceTest {

    private static final String PHONE = "13800000000";
    private static final String PHONE_HASH = "ABCDEF";
    /**
     * 🔴 不能再用 {@code Passw0rd!} 了 —— 它在已泄露密码表里（2026-10-16，P3 ③）。
     *
     * <p>这不是测试的妥协，而是这个功能的第一个产出：
     * 连我们自己的测试常量都是泄露库里的那一批，用户会写出什么可想而知。
     */
    private static final String STRONG_PASSWORD = "SvRegister2026";
    private static final String CLIENT_IP = "10.0.0.7";
    private static final long MEMBER_ID = 900001L;
    private static final String DEVICE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SMS_CODE = "123456";

    @Mock
    private MemberRegisterDao memberRegisterDao;
    @Mock
    private MemberIdAllocator memberIdAllocator;
    @Mock
    private PiiHasher piiHasher;
    @Mock
    private PiiCipher piiCipher;
    @Mock
    private MemberRiskEngine riskEngine;
    @Mock
    private DeviceDispositionService dispositionService;
    /** 真会话要往它落库。顺手也成了「这条路径有没有写流水」的探针 */
    @Mock
    private RiskEventRecorder recorder;
    @Mock
    private MemberEmailCodeService emailCodeService;
    @Mock
    private MemberSmsCodeService smsCodeService;
    /**
     * 打点用的广播口。本测试<b>不断言它</b> —— 注册成功要不要推任务进度，
     * 是任务域的判断，不该由注册的单测来钉。这里只是让构造器能装上。
     */
    @Mock
    private BizEventPublisher bizEventPublisher;
    @Mock
    private NewDeviceLoginNotifier newDeviceLoginNotifier;

    private final MemberRiskProperties riskProperties = new MemberRiskProperties();

    private MemberRegisterProperties properties;
    private MemberRegisterService service;

    @BeforeEach
    void setUp() {
        properties = new MemberRegisterProperties();
        service = new MemberRegisterService(memberRegisterDao, memberIdAllocator, properties,
                piiHasher, piiCipher, riskEngine, new WeakPasswordChecker(),
                // 真的执行器、假的下游：断言「设备被降进观察档」落在 dispositionService 上
                new RiskActionExecutor(dispositionService, mock(MemberOperationLimitService.class),
                        mock(CanaryTrap.class), mock(SuspiciousLoginNotifier.class)),
                emailCodeService, smsCodeService, bizEventPublisher, newDeviceLoginNotifier);

        when(piiHasher.hash(PHONE)).thenReturn(PHONE_HASH);
        when(piiCipher.encrypt(PHONE)).thenReturn("加密后的号");
        when(memberIdAllocator.nextMemberId()).thenReturn(MEMBER_ID);
        when(memberRegisterDao.countByPhoneHash(PHONE_HASH)).thenReturn(0);
        // 12 个参数：2026-09-09 加了 emailCipher / emailHashHex 两个
        when(memberRegisterDao.insertMember(anyLong(), anyString(), anyString(), anyInt(),
                any(), any(), any(), any(), any(), anyInt(), anyString(), anyString())).thenReturn(1);
        // 假引擎 + 真会话：结论由用例指定，落库照真发生
        when(riskEngine.open(any())).thenAnswer(i ->
                new RiskSession(i.getArgument(0), riskProperties, recorder, null));
        pre(RiskDecision.PASS);
        // 默认验证码正确：本类的其余用例关心的是【顺序】，不是验证码本身
        when(smsCodeService.verify(any(), anyString(), any())).thenReturn(SmsCodeVerifyResult.OK);
    }

    // ------------------------------------------------------------------ 正常路径

    @Test
    @DisplayName("注册成功：会员号由分配器发、账号与昵称按会员号生成")
    void 注册成功() {
        MemberRegisterResult result = service.register(cmd(PHONE, STRONG_PASSWORD));

        assertTrue(result.success());
        assertEquals(MEMBER_ID, result.identity().memberId());
        assertTrue(result.identity().memberName().endsWith(String.valueOf(MEMBER_ID)));
        // 🔴 注册即登录却不写登录日志：新设备提醒只能靠这一条认出「注册那台」
        verify(newDeviceLoginNotifier).rememberRegistrationDevice(MEMBER_ID, DEVICE_ID);
    }

    @Test
    @DisplayName("🔴 密文与摘要来自同一个规范化后的号码")
    void 密文与摘要同源() {
        service.register(cmd("138 0000 0000", STRONG_PASSWORD));

        // 不同源的表现是「解密出来的号」和「能登录的号」不是一个，而且要等到用户登不上才发现
        verify(piiCipher).encrypt(PHONE);
        verify(piiHasher).hash(PHONE);
        verify(piiCipher, never()).encrypt("138 0000 0000");
    }

    @Test
    @DisplayName("注册来源缺省不为空：留空的话来源统计从第一天起就是错的")
    void 来源缺省() {
        service.register(MemberRegisterCmd.byPhonePassword(PHONE, STRONG_PASSWORD, SMS_CODE, "H5", CLIENT_IP, null, DEVICE_ID));

        verify(memberRegisterDao).insertMember(anyLong(), anyString(), anyString(), anyInt(),
                any(), any(), any(), any(), any(), anyInt(),
                org.mockito.ArgumentMatchers.argThat(s -> s != null && !s.isBlank()), anyString());
    }

    @Test
    @DisplayName("刻意不写登录日志：注册这件事已经完整记在 t_member 的三列上")
    void 注册不写登录日志() {
        service.register(cmd(PHONE, STRONG_PASSWORD));
        // 再写一条 LOGIN_SUCCESS 只会让登录轨迹里多一条语义不同的行，
        // 查「这个人什么时候登过」时反而要先把它剔掉
        verify(memberRegisterDao).insertMember(anyLong(), anyString(), anyString(), anyInt(),
                any(), any(), any(), any(), any(), anyInt(), anyString(), anyString());
    }

    // ------------------------------------------------------------------ 顺序

    @Test
    @DisplayName("🔴 顺序：格式 → 强度 → 限频 → 查重。限频必须在查重之前")
    void 校验顺序() {
        service.register(cmd(PHONE, STRONG_PASSWORD));

        // 查重在限频之前的话，「这个号注册过没有」可以无限次免费提问
        InOrder order = inOrder(riskEngine, memberRegisterDao);
        order.verify(riskEngine).evaluatePre(any());
        order.verify(memberRegisterDao).countByPhoneHash(PHONE_HASH);
    }

    @Test
    @DisplayName("格式不合法：不消耗限频额度，也不查库")
    void 格式不合法不消耗额度() {
        MemberRegisterResult result = service.register(cmd("1380000", STRONG_PASSWORD));

        assertEquals(RegisterFailReason.BAD_PHONE_FORMAT, result.reason());
        // 手滑打错格式不该占掉他今天的注册机会 —— 连账本都不开：
        // 开了就会跑规则，而那几条规则【这一次请求本身就算一次】
        verify(riskEngine, never()).open(any());
        verify(memberRegisterDao, never()).countByPhoneHash(anyString());
    }

    @Test
    @DisplayName("🔴 密码强度够、但在已泄露表里 → 单独一档，而不是「强度不够」")
    void 已泄露密码() {
        // Password1 完全满足 8 位 + 字母 + 数字 —— 而它在每一份泄露库的前几十名里
        MemberRegisterResult result = service.register(cmd(PHONE, "Password1"));

        assertEquals(RegisterFailReason.LEAKED_PASSWORD, result.reason(),
                "并进 WEAK_PASSWORD 的话，用户会对着它反复加长度，而每次都被拒 —— 他不会明白为什么");
        // 与强度校验同一类：不查库、不开账本、不消耗任何配额
        verify(riskEngine, never()).open(any());
        verify(memberRegisterDao, never()).countByPhoneHash(anyString());
    }

    @Test
    @DisplayName("大小写变体照样挡 —— 漏掉 PASSWORD1 的泄露密码表是摆设")
    void 已泄露密码大小写() {
        assertEquals(RegisterFailReason.LEAKED_PASSWORD, service.register(cmd(PHONE, "PASSWORD1")).reason());
        assertEquals(RegisterFailReason.LEAKED_PASSWORD, service.register(cmd(PHONE, "pAsSwOrD1")).reason());
    }

    @Test
    @DisplayName("弱密码：同样排在限频之前，不查库")
    void 弱密码() {
        MemberRegisterResult result = service.register(cmd(PHONE, "123456"));

        assertEquals(RegisterFailReason.WEAK_PASSWORD, result.reason());
        verify(riskEngine, never()).open(any());
    }

    // ------------------------------------------------------------------ 前置决策

    @Test
    @DisplayName("限流 → 告诉他还要等多久，且不验码、不查重、不建号")
    void 被限流() {
        pre(new RiskDecision(MemberRiskDecisionEnum.LIMIT, 600L, Set.of()));

        MemberRegisterResult result = service.register(cmd(PHONE, STRONG_PASSWORD));

        assertEquals(RegisterFailReason.TOO_MANY_ATTEMPTS, result.reason());
        assertEquals(600L, result.retryAfterSeconds(), "让用户点第二次才知道被限，是投诉的主要来源");
        verify(smsCodeService, never()).verify(any(), anyString(), any());
        verify(memberRegisterDao, never()).countByPhoneHash(anyString());
    }

    @Test
    @DisplayName("🔴 拒绝（黑名单 / 设备封禁）对外与限流【同一句】")
    void 拒绝与限流同一句() {
        pre(new RiskDecision(MemberRiskDecisionEnum.DENY, 3600L, Set.of()));

        MemberRegisterResult result = service.register(cmd(PHONE, STRONG_PASSWORD));

        // 告诉对方「你这个 IP 被拉黑了」等于给他一个精确的调试反馈，他换一个就继续
        assertEquals(RegisterFailReason.TOO_MANY_ATTEMPTS, result.reason());
    }

    @Test
    @DisplayName("🔴 配成了注册执行不了的处置（滑块 / OTP）→ 放行，不是静默拦住")
    void 执行不了的处置按放行() {
        pre(new RiskDecision(MemberRiskDecisionEnum.OTP, 0L, Set.of()));

        // 把一个配置错误变成「全站注册不可用」，代价完全不对称。
        // 真正的防线在保存与加载那一步（各规则的 validate / RiskRuleRegistry.toConfig）
        assertTrue(service.register(cmd(PHONE, STRONG_PASSWORD)).success());
    }

    @Test
    @DisplayName("生效结论带的观察档动作要真的执行 —— 不然 params.observe 是句空话")
    void 执行观察档动作() {
        pre(new RiskDecision(MemberRiskDecisionEnum.LIMIT, 60L, Set.of(RiskAction.OBSERVE_DEVICE)));

        service.register(cmd(PHONE, STRONG_PASSWORD));

        verify(dispositionService).observe(eq(DEVICE_ID), anyString());
    }

    @Test
    @DisplayName("🔴 上下文：场景是注册、member 恒为 null、没有滑块票")
    void 上下文() {
        service.register(cmd(PHONE, STRONG_PASSWORD));

        ArgumentCaptor<solvela.member.risk.RiskContext> ctx =
                ArgumentCaptor.forClass(solvela.member.risk.RiskContext.class);
        verify(riskEngine).open(ctx.capture());

        assertEquals(MemberRiskEventTypeEnum.REGISTER, ctx.getValue().getEventType());
        // 注册的全部意义就是这个人还没有账号：用到 member 的规则在这个场景一条都没有
        assertNull(ctx.getValue().getMember());
        // 滑块票由网关在【发码】那一步核销，注册接口收不到
        assertFalse(ctx.getValue().isCaptchaPassed());
        assertEquals(PHONE_HASH, ctx.getValue().getIdentityHashHex());
        assertEquals(MemberLoginType.PHONE_PASSWORD, ctx.getValue().getLoginType());
    }

    // ------------------------------------------------------------------ 流水

    @Test
    @DisplayName("🔴 每一条返回路径都落一行流水，而且结局各不相同")
    void 四种结局都落库() {
        assertOutcome(MemberRiskOutcomeEnum.SUCCESS, () -> service.register(cmd(PHONE, STRONG_PASSWORD)));

        pre(new RiskDecision(MemberRiskDecisionEnum.LIMIT, 60L, Set.of()));
        assertOutcome(MemberRiskOutcomeEnum.PRE_BLOCKED, () -> service.register(cmd(PHONE, STRONG_PASSWORD)));

        pre(RiskDecision.PASS);
        when(smsCodeService.verify(any(), anyString(), any())).thenReturn(SmsCodeVerifyResult.MISMATCH);
        assertOutcome(MemberRiskOutcomeEnum.BAD_CREDENTIALS, () -> service.register(cmd(PHONE, STRONG_PASSWORD)));

        when(smsCodeService.verify(any(), anyString(), any())).thenReturn(SmsCodeVerifyResult.OK);
        when(memberRegisterDao.countByPhoneHash(PHONE_HASH)).thenReturn(1);
        // 「这个号已经有人了」是注册这边信号最强的一个结局：
        // 并进「凭据错」的话，拿着泄露号码库挨个试的那个形状在看板上就看不见了
        assertOutcome(MemberRiskOutcomeEnum.IDENTITY_TAKEN, () -> service.register(cmd(PHONE, STRONG_PASSWORD)));
    }

    private void assertOutcome(MemberRiskOutcomeEnum expected, Runnable action) {
        clearInvocations(recorder);
        action.run();
        ArgumentCaptor<MemberRiskEvent> event = ArgumentCaptor.forClass(MemberRiskEvent.class);
        verify(recorder).record(event.capture(), org.mockito.ArgumentMatchers.anyList());
        assertEquals(expected, event.getValue().getOutcome());
        assertEquals(MemberRiskEventTypeEnum.REGISTER, event.getValue().getEventType());
    }

    // ------------------------------------------------------------------ 查重与并发

    @Test
    @DisplayName("手机号已注册：给人话，不建号")
    void 手机号已注册() {
        when(memberRegisterDao.countByPhoneHash(PHONE_HASH)).thenReturn(1);

        assertEquals(RegisterFailReason.PHONE_TAKEN, service.register(cmd(PHONE, STRONG_PASSWORD)).reason());
        verify(memberIdAllocator, never()).nextMemberId();
    }

    @Test
    @DisplayName("🔴 并发撞唯一约束：收成「已被注册」，不是 500")
    void 并发重复注册() {
        // 查重和插入之间的窗口，靠库上的唯一约束闭合
        when(memberRegisterDao.insertMember(anyLong(), anyString(), anyString(), anyInt(),
                any(), any(), any(), any(), any(), anyInt(), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("uk_member_phone_hash"));

        MemberRegisterResult result = service.register(cmd(PHONE, STRONG_PASSWORD));

        assertEquals(RegisterFailReason.PHONE_TAKEN, result.reason(),
                "这是完全预期内的结果，抛出去就变成 500 了");
    }

    // ------------------------------------------------------------------ 短信验证码

    @Test
    @DisplayName("🔴 验证码不对 → 拒绝，而且【不查重、不建号】")
    void 验证码不对() {
        when(smsCodeService.verify(any(), anyString(), any()))
                .thenReturn(SmsCodeVerifyResult.MISMATCH);

        MemberRegisterResult result = service.register(cmd(PHONE, STRONG_PASSWORD));

        assertFalse(result.success());
        assertEquals(RegisterFailReason.SMS_CODE_MISMATCH, result.reason());
        verify(memberRegisterDao, never()).countByPhoneHash(anyString());
    }

    @Test
    @DisplayName("三种验证码失败各自映射到自己的 reason —— 客户端据此决定「重填」还是「重发」")
    void 验证码三种失败() {
        when(smsCodeService.verify(any(), anyString(), any()))
                .thenReturn(SmsCodeVerifyResult.NOT_FOUND);
        assertEquals(RegisterFailReason.SMS_CODE_EXPIRED,
                service.register(cmd(PHONE, STRONG_PASSWORD)).reason());

        when(smsCodeService.verify(any(), anyString(), any()))
                .thenReturn(SmsCodeVerifyResult.TOO_MANY_ATTEMPTS);
        assertEquals(RegisterFailReason.SMS_CODE_LOCKED,
                service.register(cmd(PHONE, STRONG_PASSWORD)).reason());
    }

    @Test
    @DisplayName("🔴 验证码夹在限频【之后】、查重【之前】—— 两个边界各有理由")
    void 验证码的位置() {
        service.register(cmd(PHONE, STRONG_PASSWORD));

        InOrder order = inOrder(riskEngine, smsCodeService, memberRegisterDao);
        // 之后：验码要读 Redis 还要写回失败计数，已经被限频挡下的请求不该做这些
        order.verify(riskEngine).evaluatePre(any());
        order.verify(smsCodeService).verify(eq(SmsScene.REGISTER), eq(PHONE), any());
        // 之前：否则没有验证码的人也能拿注册接口反复问「这个号注册过没有」
        order.verify(memberRegisterDao).countByPhoneHash(PHONE_HASH);
    }

    @Test
    @DisplayName("验证码用【规范化后】的号码去验 —— 发码那边也是规范化后的，两边必须同一个键")
    void 验证码用规范化后的号码() {
        service.register(cmd("+86 138 0000 0000", STRONG_PASSWORD));

        verify(smsCodeService).verify(eq(SmsScene.REGISTER), eq(PHONE), any());
    }

    @Test
    @DisplayName("phone-code-required=false → 完全不碰验证码服务（接厂商之前的过渡开关）")
    void 开关关掉时不验码() {
        properties.setPhoneCodeRequired(false);

        assertTrue(service.register(cmd(PHONE, STRONG_PASSWORD)).success());

        verify(smsCodeService, never()).verify(any(), anyString(), any());
    }

    private void pre(RiskDecision decision) {
        when(riskEngine.evaluatePre(any())).thenReturn(decision);
    }

    private MemberRegisterCmd cmd(String phone, String password) {
        return MemberRegisterCmd.byPhonePassword(phone, password, SMS_CODE, "H5", CLIENT_IP, "APP", DEVICE_ID);
    }
}
