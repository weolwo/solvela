package solvela.member.auth;

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
import solvela.crypto.PiiHasher;
import solvela.enums.LoginLogResultEnum;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberStatusEnum;
import solvela.member.Member;
import solvela.member.MemberLoginLog;
import solvela.member.MemberOperationLimit;
import solvela.member.api.AuthFailReason;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.SmsCodeSendResult;
import solvela.member.api.LoginChallengeCodeResult;
import solvela.member.api.LoginChallengeCmd;
import solvela.member.device.DeviceDispositionService;
import solvela.member.device.DeviceGuard;
import solvela.member.sms.MemberSmsCodeService;
import solvela.member.api.SmsCodeVerifyResult;
import solvela.member.api.SmsScene;
import solvela.member.api.MemberLoginType;
import solvela.enums.DeviceStatusEnum;
import solvela.member.device.DeviceGuardRule;
import solvela.member.device.DeviceGuardVerdict;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.member.operationlimit.service.MemberOperationLimitService;
import solvela.member.register.MemberRegisterService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
 * <h3>这里钉的四件事</h3>
 * <ul>
 *   <li><b>状态在验密码之前</b>。反过来的话，被冻结的账号还能拿来试探密码对不对 ——
 *       一个已经被判定为高风险的账号，不该再提供任何「密码猜对了没有」的信号；</li>
 *   <li><b>限制在验密码之前</b>。放到后面，被限期间每次尝试仍然会走一遍密码比对，
 *       限制就只剩一句提示语，拦不住任何东西；</li>
 *   <li><b>失败要记数、成功要清数</b>。少了清零那一步，用户今天错两次、下周再错三次
 *       就被锁 —— 而他两次都成功登录过。这类问题的报障描述是「莫名其妙被锁」，
 *       从日志里根本看不出来；</li>
 *   <li><b>写登录日志失败绝不能影响登录</b>。「登不上去是因为日志表满了」排查极其费劲。</li>
 * </ul>
 *
 * <h3>手机号必须先规范化再算摘要</h3>
 * {@code "138 0000 0000"} 和 {@code "13800000000"} 的 hash 不一样。顺序错了的表现是
 * 「同一个号有时能登有时不能」，取决于用户这次有没有手滑打空格。
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
    private MemberOperationLimitService operationLimitService;
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
    @Mock
    private DeviceDispositionService dispositionService;
    @Mock
    private MemberSmsCodeService smsCodeService;

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

        when(piiHasher.hash(PHONE)).thenReturn(PHONE_HASH);
        when(memberAuthDao.selectForLogin(PHONE_HASH)).thenReturn(member);
        when(operationLimitService.getActiveLimit(anyLong(), any())).thenReturn(null);
        // 默认设备闸放行：绝大多数用例关心的是它【之后】的分支顺序
        when(deviceGuard.checkLogin(any())).thenReturn(DeviceGuardVerdict.pass());
        when(deviceGuard.checkMemberFanout(any(), any())).thenReturn(DeviceGuardVerdict.pass());
        // 默认设备是正常档：观察档那条支线由 checkDeviceChallenge 自己的用例覆盖
        when(dispositionService.currentStatus(any())).thenReturn(DeviceStatusEnum.NORMAL);
    }

    // ------------------------------------------------------------------ 正常路径

    @Test
    @DisplayName("认证通过：返回身份、清空失败计数、记一条成功日志")
    void 认证通过() {
        MemberAuthResult result = service.authenticate(cmd(RAW_PASSWORD));

        assertTrue(result.success());
        assertEquals(MEMBER_ID, result.identity().memberId());
        assertEquals("小明", result.identity().nickname());
        // 少了这一步，用户今天错两次、下周再错三次就被锁，而他两次都登录成功过
        verify(operationLimitService).clearFail(MEMBER_ID, MemberOperationTypeEnum.LOGIN);
        assertEquals(LoginLogResultEnum.LOGIN_SUCCESS, savedLog().getStatus());
    }

    @Test
    @DisplayName("🔴 手机号先规范化再算摘要：带空格和带 +86 的写法要落到同一个 hash")
    void 手机号规范化后再算摘要() {
        service.authenticate(cmd("138 0000 0000", RAW_PASSWORD));
        service.authenticate(cmd("+8613800000000", RAW_PASSWORD));

        // 顺序错了的表现是「同一个号有时能登有时不能」，取决于用户这次有没有手滑打空格
        verify(piiHasher, org.mockito.Mockito.times(2)).hash(PHONE);
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
        // 没走到密码分支，所以不该记失败次数
        verify(operationLimitService, never()).recordFail(anyLong(), any(), anyString());
        assertEquals("账号已冻结", savedLog().getRemark());
    }

    @Test
    @DisplayName("🔴 限制生效期间直接挡回：连试的机会都不给，否则限制形同虚设")
    void 限制期间不验密码() {
        when(operationLimitService.getActiveLimit(MEMBER_ID, MemberOperationTypeEnum.LOGIN))
                .thenReturn(limitExpiringIn(120));

        MemberAuthResult result = service.authenticate(cmd("随便一个错密码"));

        assertEquals(AuthFailReason.OPERATION_LIMITED, result.reason());
        assertTrue(result.lockedSeconds() > 0 && result.lockedSeconds() <= 120,
                "要告诉用户还得等多久，含糊其辞只会把人逼去打客服电话");
        verify(operationLimitService, never()).recordFail(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("检查顺序：状态 → 限制 → 密码，一步都不能提前")
    void 分支顺序() {
        service.authenticate(cmd(RAW_PASSWORD));

        InOrder order = inOrder(memberAuthDao, operationLimitService);
        order.verify(memberAuthDao).selectForLogin(anyString());
        order.verify(operationLimitService).getActiveLimit(MEMBER_ID, MemberOperationTypeEnum.LOGIN);
        order.verify(operationLimitService).clearFail(MEMBER_ID, MemberOperationTypeEnum.LOGIN);
    }

    // ------------------------------------------------------------------ 失败

    @Test
    @DisplayName("手机号格式不合法：不查库、不写日志")
    void 手机号格式不合法() {
        MemberAuthResult result = service.authenticate(cmd("1380000", RAW_PASSWORD));

        assertEquals(AuthFailReason.BAD_PHONE_FORMAT, result.reason());
        verify(memberAuthDao, never()).selectForLogin(anyString());
        verify(memberLoginLogDao, never()).insert(any(MemberLoginLog.class));
    }

    @Test
    @DisplayName("手机号查无此人：给「凭证错误」，且不写登录日志（member_id 非空，没人可记）")
    void 手机号不存在() {
        when(memberAuthDao.selectForLogin(PHONE_HASH)).thenReturn(null);

        MemberAuthResult result = service.authenticate(cmd(RAW_PASSWORD));

        // 与「密码错」给同一个原因：区分开来等于送出一个手机号枚举接口
        assertEquals(AuthFailReason.BAD_CREDENTIALS, result.reason());
        verify(memberLoginLogDao, never()).insert(any(MemberLoginLog.class));
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
        verify(operationLimitService, never()).recordFail(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("密码错误：记一次失败、写失败日志，未达阈值时返回「凭证错误」")
    void 密码错误() {
        when(operationLimitService.recordFail(anyLong(), any(), anyString())).thenReturn(null);

        MemberAuthResult result = service.authenticate(cmd("WrongPass1!"));

        assertEquals(AuthFailReason.BAD_CREDENTIALS, result.reason());
        verify(operationLimitService).recordFail(MEMBER_ID, MemberOperationTypeEnum.LOGIN, "连续登录失败");
        verify(operationLimitService, never()).clearFail(anyLong(), any());
        assertEquals(LoginLogResultEnum.LOGIN_FAIL, savedLog().getStatus());
    }

    @Test
    @DisplayName("🔴 这一次失败刚好触发限制：当场返回「还要等多久」，不让用户再点一次才发现")
    void 失败触发限制当场告知() {
        when(operationLimitService.recordFail(anyLong(), any(), anyString()))
                .thenReturn(limitExpiringIn(1800));

        MemberAuthResult result = service.authenticate(cmd("WrongPass1!"));

        assertEquals(AuthFailReason.OPERATION_LIMITED, result.reason());
        assertTrue(result.lockedSeconds() > 0, "让用户点第二次才知道被限，是投诉的主要来源");
    }

    @Test
    @DisplayName("限制已过期：剩余秒数按 0 算，不返回负数")
    void 剩余秒数不为负() {
        when(operationLimitService.getActiveLimit(MEMBER_ID, MemberOperationTypeEnum.LOGIN))
                .thenReturn(limitExpiringIn(-60));

        // 负数会被调用方格式化成「请 -1 分钟后重试」
        assertEquals(0L, service.authenticate(cmd(RAW_PASSWORD)).lockedSeconds());
    }

    // ------------------------------------------------------------------ 日志

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

    // ------------------------------------------------------------------ 辅助

    private MemberAuthCmd cmd(String password) {
        return cmd(PHONE, password);
    }

    private MemberAuthCmd cmd(String phone, String password) {
        return MemberAuthCmd.byPhonePassword(phone, password, "H5", "127.0.0.1", DEVICE_ID);
    }

    /** 带二次验证码的登录命令。 */
    private MemberAuthCmd cmdWithCode(String code) {
        return new MemberAuthCmd(MemberLoginType.PHONE_PASSWORD, PHONE, RAW_PASSWORD, code,
                "H5", "127.0.0.1", DEVICE_ID);
    }

    /** 把这台设备置成观察档。 */
    private void deviceUnderObservation() {
        when(dispositionService.currentStatus(DEVICE_ID)).thenReturn(DeviceStatusEnum.OBSERVE);
    }

    // ------------------------------------------------------------------ 设备观察档

    @Test
    @DisplayName("🔴 观察档 + 密码对 → DEVICE_VERIFICATION_REQUIRED，并签一张绑在这台设备上的凭票")
    void 观察档要二次验证() {
        deviceUnderObservation();
        when(challengeStore.issue(any())).thenReturn("lc_ticket");

        MemberAuthResult result = service.authenticate(cmd(PHONE, RAW_PASSWORD));

        assertFalse(result.success());
        assertEquals(AuthFailReason.DEVICE_VERIFICATION_REQUIRED, result.reason(),
                "密码是对的，只是还差一步。回成 BAD_CREDENTIALS 的话，"
                        + "用户会一直以为自己密码记错了，去走找回密码 —— 而那解决不了他的问题");
        assertEquals("lc_ticket", result.challengeTicket());
        verify(challengeStore).issue(eq(new LoginChallengeStore.Challenge(
                MEMBER_ID, MemberLoginType.PHONE_PASSWORD, DEVICE_ID, "H5", PHONE)));
    }

    @Test
    @DisplayName("🔴 观察档 + 登录请求里自带了码 → 不再直接放行，照样要走凭票")
    void 自带码不再放行() {
        deviceUnderObservation();
        when(challengeStore.issue(any())).thenReturn("lc_ticket");

        MemberAuthResult result = service.authenticate(cmdWithCode("123456"));

        assertEquals(AuthFailReason.DEVICE_VERIFICATION_REQUIRED, result.reason(),
                "旧路径（带着码重新登录）的码来自匿名发码接口 —— 那个口子已经关了，这条路也不能再通");
        verify(smsCodeService, never()).verify(any(), any(), any());
    }

    // ------------------------------------------------------------------ 凭票发码 / 验码

    private static final String TICKET = "lc_ticket";

    private void validTicket() {
        when(challengeStore.find(TICKET)).thenReturn(new LoginChallengeStore.Challenge(
                MEMBER_ID, MemberLoginType.PHONE_PASSWORD, DEVICE_ID, "H5", PHONE));
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
    @DisplayName("凭票验码 + 码对 → 登录成功，票作废，照常写成功日志")
    void 凭票码对() {
        validTicket();
        when(smsCodeService.verify(SmsScene.LOGIN, PHONE, "123456")).thenReturn(SmsCodeVerifyResult.OK);
        when(memberAuthDao.selectForAuth(MEMBER_ID)).thenReturn(member);

        MemberAuthResult result = service.verifyChallenge(ticketCmd("123456", DEVICE_ID));

        assertTrue(result.success());
        assertEquals(MEMBER_ID, result.identity().memberId());
        verify(challengeStore).discard(TICKET);
        verify(deviceGuard).checkMemberFanout(DEVICE_ID, MEMBER_ID);
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

    @Test
    @DisplayName("🔴 二次验证排在密码校验【之后】—— 否则谁都能拿一个手机号让我们发短信")
    void 二次验证排在密码之后() {
        deviceUnderObservation();

        MemberAuthResult result = service.authenticate(
                new MemberAuthCmd(MemberLoginType.PHONE_PASSWORD, PHONE, "WrongPassword9", "123456",
                        "H5", "127.0.0.1", DEVICE_ID));

        assertEquals(AuthFailReason.BAD_CREDENTIALS, result.reason(),
                "密码错就该在密码那一关挡下，不该走到二次验证");
        verify(smsCodeService, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("正常档设备完全不受影响 —— 绝大多数登录不该多一次往返")
    void 正常档不要二次验证() {
        assertTrue(service.authenticate(cmd(PHONE, RAW_PASSWORD)).success());

        verify(smsCodeService, never()).verify(any(), any(), any());
    }

    private MemberOperationLimit limitExpiringIn(long seconds) {
        MemberOperationLimit limit = new MemberOperationLimit();
        limit.setMemberId(MEMBER_ID);
        limit.setExpireTime(LocalDateTime.now().plusSeconds(seconds));
        return limit;
    }

    private MemberLoginLog savedLog() {
        ArgumentCaptor<MemberLoginLog> captor = ArgumentCaptor.forClass(MemberLoginLog.class);
        verify(memberLoginLogDao).insert(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ 设备闸

    @Test
    @DisplayName("🔴 设备闸拦下时，压根不去查会员 —— 否则登录接口就成了手机号枚举器")
    void 设备被限时不查会员() {
        when(deviceGuard.checkLogin(DEVICE_ID))
                .thenReturn(DeviceGuardVerdict.hit(DeviceGuardRule.LOGIN_TOO_MANY, 600L, false));

        MemberAuthResult result = service.authenticate(cmd(PHONE, RAW_PASSWORD));

        assertFalse(result.success());
        assertEquals(AuthFailReason.DEVICE_LIMITED, result.reason());
        assertEquals(600L, result.lockedSeconds());
        verify(memberAuthDao, never()).selectForLogin(any());
        verify(memberLoginLogDao, never()).insert(any(MemberLoginLog.class));
    }

    @Test
    @DisplayName("设备闸命中但 dry-run 放行 → 登录照常成功")
    void dryRun命中不影响登录() {
        when(deviceGuard.checkLogin(DEVICE_ID))
                .thenReturn(DeviceGuardVerdict.hit(DeviceGuardRule.LOGIN_TOO_MANY, 600L, true));

        assertTrue(service.authenticate(cmd(PHONE, RAW_PASSWORD)).success(),
                "dry-run 期间命中了也必须放行 —— 否则这一档没有存在的意义");
    }

    @Test
    @DisplayName("密码错时，账号锁与设备失败计数【两边都要记】")
    void 密码错两边都记() {
        service.authenticate(cmd(PHONE, "WrongPassword9"));

        verify(operationLimitService).recordFail(eq(MEMBER_ID), any(), any());
        verify(deviceGuard).recordLoginFailure(DEVICE_ID);
    }

    @Test
    @DisplayName("一机多号被拦 → 密码验过了也不放行，并记一条失败日志")
    void 一机多号拦在成功之后() {
        when(deviceGuard.checkMemberFanout(DEVICE_ID, MEMBER_ID))
                .thenReturn(DeviceGuardVerdict.hit(DeviceGuardRule.MEMBER_FANOUT, 3600L, false));

        MemberAuthResult result = service.authenticate(cmd(PHONE, RAW_PASSWORD));

        assertFalse(result.success());
        assertEquals(AuthFailReason.DEVICE_LIMITED, result.reason());
    }
}
