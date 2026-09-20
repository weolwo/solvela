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
import solvela.base.event.BizEventPublisher;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.member.device.DeviceGuard;
import solvela.member.email.MemberEmailCodeService;
import solvela.member.sms.MemberSmsCodeService;
import solvela.member.api.SmsCodeVerifyResult;
import solvela.member.api.SmsScene;
import solvela.member.device.DeviceGuardRule;
import solvela.member.device.DeviceGuardVerdict;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.RegisterFailReason;
import solvela.member.id.MemberIdAllocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
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
    private static final String STRONG_PASSWORD = "Passw0rd!";
    private static final String CLIENT_IP = "10.0.0.7";
    private static final long MEMBER_ID = 900001L;
    private static final String DEVICE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SMS_CODE = "123456";

    @Mock
    private MemberRegisterDao memberRegisterDao;
    @Mock
    private MemberIdAllocator memberIdAllocator;
    @Mock
    private RedisService redisService;
    @Mock
    private PiiHasher piiHasher;
    @Mock
    private PiiCipher piiCipher;
    @Mock
    private DeviceGuard deviceGuard;
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

    private MemberRegisterProperties properties;
    private MemberRegisterService service;

    @BeforeEach
    void setUp() {
        properties = new MemberRegisterProperties();
        service = new MemberRegisterService(memberRegisterDao, memberIdAllocator, properties,
                redisService, piiHasher, piiCipher, deviceGuard, emailCodeService, smsCodeService,
                bizEventPublisher);

        when(piiHasher.hash(PHONE)).thenReturn(PHONE_HASH);
        when(piiCipher.encrypt(PHONE)).thenReturn("加密后的号");
        when(memberIdAllocator.nextMemberId()).thenReturn(MEMBER_ID);
        when(memberRegisterDao.countByPhoneHash(PHONE_HASH)).thenReturn(0);
        // 12 个参数：2026-09-09 加了 emailCipher / emailHashHex 两个
        when(memberRegisterDao.insertMember(anyLong(), anyString(), anyString(), anyInt(),
                any(), any(), any(), any(), any(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(redisService.generateRedisKey(anyString(), anyString())).thenReturn("k");
        // 默认设备闸放行：本类关心的是格式 → 强度 → 限频 → 查重这条顺序
        when(deviceGuard.checkRegister(any())).thenReturn(DeviceGuardVerdict.pass());
        // 默认放行：第 1 次尝试，上限 10
        when(redisService.increment(anyString(), anyLong())).thenReturn(1L);
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
        InOrder order = inOrder(redisService, memberRegisterDao);
        order.verify(redisService).increment(anyString(), anyLong());
        order.verify(memberRegisterDao).countByPhoneHash(PHONE_HASH);
    }

    @Test
    @DisplayName("格式不合法：不消耗限频额度，也不查库")
    void 格式不合法不消耗额度() {
        MemberRegisterResult result = service.register(cmd("1380000", STRONG_PASSWORD));

        assertEquals(RegisterFailReason.BAD_PHONE_FORMAT, result.reason());
        // 手滑打错格式不该占掉他今天的注册机会
        verify(redisService, never()).increment(anyString(), anyLong());
        verify(memberRegisterDao, never()).countByPhoneHash(anyString());
    }

    @Test
    @DisplayName("弱密码：同样排在限频之前，不查库")
    void 弱密码() {
        MemberRegisterResult result = service.register(cmd(PHONE, "123456"));

        assertEquals(RegisterFailReason.WEAK_PASSWORD, result.reason());
        verify(redisService, never()).increment(anyString(), anyLong());
    }

    // ------------------------------------------------------------------ 限频

    @Test
    @DisplayName("超过 IP 配额：告诉他还要等多久，且不查重不建号")
    void 超出IP配额() {
        when(redisService.increment(anyString(), anyLong()))
                .thenReturn((long) properties.getMaxAttemptsPerIp() + 1);
        when(redisService.getExpire(anyString())).thenReturn(600L);

        MemberRegisterResult result = service.register(cmd(PHONE, STRONG_PASSWORD));

        assertEquals(RegisterFailReason.TOO_MANY_ATTEMPTS, result.reason());
        assertEquals(600L, result.retryAfterSeconds(), "让用户点第二次才知道被限，是投诉的主要来源");
        verify(memberRegisterDao, never()).countByPhoneHash(anyString());
    }

    @Test
    @DisplayName("恰好用满配额那一次仍然放行（阈值是「超过」不是「达到」）")
    void 用满配额仍放行() {
        when(redisService.increment(anyString(), anyLong()))
                .thenReturn((long) properties.getMaxAttemptsPerIp());

        assertTrue(service.register(cmd(PHONE, STRONG_PASSWORD)).success());
    }

    @Test
    @DisplayName("限频键没有 TTL 时至少报 1 秒，不返回 0 或负数")
    void 等待秒数至少一秒() {
        when(redisService.increment(anyString(), anyLong()))
                .thenReturn((long) properties.getMaxAttemptsPerIp() + 1);
        // -1 = 键存在但没有过期时间，-2 = 键不存在
        when(redisService.getExpire(anyString())).thenReturn(-1L);

        // 返回 0 的话，前端的「x 秒后重试」会显示成「0 秒后重试」然后立刻又被拒
        assertEquals(1L, service.register(cmd(PHONE, STRONG_PASSWORD)).retryAfterSeconds());
    }

    @Test
    @DisplayName("🔴 拿不到客户端 IP 时放行，不是一律拒绝")
    void 没有IP时放行() {
        MemberRegisterResult result =
                service.register(MemberRegisterCmd.byPhonePassword(PHONE, STRONG_PASSWORD, SMS_CODE, "H5", null, "APP", DEVICE_ID));

        // 一律拒绝会让任何一次取 IP 失败变成「全站注册不可用」，那种故障比放过几个注册严重得多
        assertTrue(result.success());
        verify(redisService, never()).increment(anyString(), anyLong());
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

        InOrder order = inOrder(deviceGuard, smsCodeService, memberRegisterDao);
        // 之后：验码要读 Redis 还要写回失败计数，已经被限频挡下的请求不该做这些
        order.verify(deviceGuard).checkRegister(any());
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

    private MemberRegisterCmd cmd(String phone, String password) {
        return MemberRegisterCmd.byPhonePassword(phone, password, SMS_CODE, "H5", CLIENT_IP, "APP", DEVICE_ID);
    }

    // ------------------------------------------------------------------ 设备闸

    @Test
    @DisplayName("🔴 设备闸拦下时，不消耗 IP 配额、不查重、不建号")
    void 设备被限时什么都不做() {
        when(deviceGuard.checkRegister(DEVICE_ID))
                .thenReturn(DeviceGuardVerdict.hit(DeviceGuardRule.REGISTER_TOO_MANY, 7200L, false));

        MemberRegisterResult result = service.register(cmd(PHONE, STRONG_PASSWORD));

        assertFalse(result.success());
        assertEquals(RegisterFailReason.DEVICE_LIMITED, result.reason());
        assertEquals(7200L, result.retryAfterSeconds());
        verify(redisService, never()).increment(anyString(), anyLong());
        verify(memberRegisterDao, never()).countByPhoneHash(any());
    }

    @Test
    @DisplayName("设备闸排在 IP 限频【之前】—— 更硬的那把先筛")
    void 设备闸在IP限频之前() {
        when(deviceGuard.checkRegister(DEVICE_ID))
                .thenReturn(DeviceGuardVerdict.hit(DeviceGuardRule.REGISTER_TOO_MANY, 7200L, false));

        service.register(cmd(PHONE, STRONG_PASSWORD));

        // IP 计数一次都没走：反过来的话，一台设备能把它所在 IP 的配额顺手烧光
        verify(redisService, never()).increment(anyString(), anyLong());
    }
}
