package solvela.member.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.domain.SystemEnvironment;
import solvela.base.enumeration.SystemEnvironmentEnum;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.member.api.SmsCodeFailReason;
import solvela.member.api.SmsCodeVerifyResult;
import solvela.member.api.SmsScene;
import solvela.member.code.VerificationCodeProperties;
import solvela.member.code.VerificationCodeStore;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信验证码。<b>刻意不镜像 {@code MemberEmailCodeServiceTest} 的那 22 条</b>。
 *
 * <h3>为什么不抄一遍</h3>
 * 次数上限、验错不续期、用一次即作废、常数时间比较 —— 这些性质全在
 * {@link VerificationCodeStore} 里，两条通道<b>共用同一份代码</b>。
 * 再抄一套用例，验的是同一批 if，代价却是「改一次实现要改两处测试」。
 *
 * <p>所以这里只验短信自己的那几件事：
 * <ul>
 *   <li>LOG 通道一条短信都不发（没接厂商的当下，那是唯一能跑的路）；</li>
 *   <li>发送失败要<b>把码删掉</b> —— 不删的话用户收不到短信还被冷却挡住；</li>
 *   <li>两个启动守卫：生产不许 LOG，生产也不许「REAL 但没有厂商」。</li>
 * </ul>
 *
 * @Date 2026-09-10
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberSmsCodeServiceTest {

    private static final String PHONE = "13800138000";

    private static final String HASH = "abcdef0123456789";

    private static final String IP = "10.0.0.1";

    @Mock
    private RedisService redisService;
    @Mock
    private SmsSender smsSender;
    @Mock
    private PiiHasher piiHasher;

    private VerificationCodeProperties properties;

    /** 模拟 Redis：本类只用得到一个码键。 */
    private final Map<String, String> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        properties = new VerificationCodeProperties();
        when(piiHasher.hash(anyString())).thenReturn(HASH);
        when(redisService.generateRedisKey(anyString(), anyString()))
                .thenAnswer(i -> i.<String>getArgument(0) + i.<String>getArgument(1));
        when(redisService.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        when(redisService.getExpire(anyString())).thenReturn(300L);
        // 计数键默认没超
        when(redisService.increment(anyString(), anyLong())).thenReturn(1L);
        when(smsSender.available()).thenReturn(true);
        org.mockito.Mockito.doAnswer(i -> {
            store.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(redisService).set(anyString(), anyString(), anyLong());
        org.mockito.Mockito.doAnswer(i -> {
            store.remove(i.<String>getArgument(0));
            return null;
        }).when(redisService).delete(anyString());
    }

    private MemberSmsCodeService service(VerificationCodeProperties.Transport transport,
                                         SystemEnvironmentEnum env) {
        properties.setSmsTransport(transport);
        return new MemberSmsCodeService(new VerificationCodeStore(redisService, properties),
                smsSender, piiHasher, properties,
                // ⚠️ isProd 是【独立的一个字段】，不是从枚举推出来的 ——
                //    两者填得不一致时，SystemEnvironment 不会报错，只会让守卫悄悄失效
                new SystemEnvironment(env == SystemEnvironmentEnum.PROD, "solvela", env));
    }

    private String storedCode() {
        return store.values().iterator().next().split("\\|")[0];
    }

    // ============================== 发送 ==============================

    @Test
    @DisplayName("REAL 通道：码存下来、短信发出去，且发送器拿到的是【规范化后】的号码")
    void 真发() {
        MemberSmsCodeService service =
                service(VerificationCodeProperties.Transport.REAL, SystemEnvironmentEnum.DEV);

        assertTrue(service.send(SmsScene.REGISTER, "+86 138 0013 8000", IP).success());

        verify(smsSender).send(eq(PHONE), eq(SmsScene.REGISTER), anyString(), anyLong());
    }

    @Test
    @DisplayName("🔴 LOG 通道：一条短信都不发 —— 没接厂商的当下，这是唯一跑得通的路")
    void LOG通道不碰发送器() {
        MemberSmsCodeService service =
                service(VerificationCodeProperties.Transport.LOG, SystemEnvironmentEnum.DEV);

        assertTrue(service.send(SmsScene.REGISTER, PHONE, IP).success());

        verify(smsSender, never()).send(anyString(), any(), anyString(), anyLong());
        assertFalse(store.isEmpty(), "码还是要存的 —— 用户要拿日志里那个码去验");
    }

    @Test
    @DisplayName("🔴 发送失败 → 把刚存的码删掉，否则用户收不到短信还被冷却挡住")
    void 发送失败要删码() {
        MemberSmsCodeService service =
                service(VerificationCodeProperties.Transport.REAL, SystemEnvironmentEnum.DEV);
        doThrow(new IllegalStateException("厂商挂了"))
                .when(smsSender).send(anyString(), any(), anyString(), anyLong());

        assertEquals(SmsCodeFailReason.SEND_FAILED,
                service.send(SmsScene.REGISTER, PHONE, IP).reason());

        assertTrue(store.isEmpty(),
                "留着这个码，用户在收不到短信的同时还会被告知「请稍后再试」—— 他什么也做不了");
    }

    @Test
    @DisplayName("号码格式不对 → 不发，也不消耗任何配额（那一档是真花钱的）")
    void 格式不对() {
        MemberSmsCodeService service =
                service(VerificationCodeProperties.Transport.REAL, SystemEnvironmentEnum.DEV);

        assertEquals(SmsCodeFailReason.BAD_PHONE_FORMAT,
                service.send(SmsScene.REGISTER, "not-a-phone", IP).reason());

        verify(redisService, never()).increment(anyString(), anyLong());
    }

    // ============================== 校验 ==============================

    @Test
    @DisplayName("🔴 场景隔离：为注册发的码，拿去重置密码验不过")
    void 场景隔离() {
        MemberSmsCodeService service =
                service(VerificationCodeProperties.Transport.LOG, SystemEnvironmentEnum.DEV);
        assertTrue(service.send(SmsScene.REGISTER, PHONE, IP).success());
        String code = storedCode();

        assertEquals(SmsCodeVerifyResult.NOT_FOUND,
                service.verify(SmsScene.RESET_PASSWORD, PHONE, code));
        assertEquals(SmsCodeVerifyResult.OK, service.verify(SmsScene.REGISTER, PHONE, code));
    }

    @Test
    @DisplayName("验码也走规范化 —— 发码那边同样，两边必须落在同一个键上")
    void 验码规范化() {
        MemberSmsCodeService service =
                service(VerificationCodeProperties.Transport.LOG, SystemEnvironmentEnum.DEV);
        assertTrue(service.send(SmsScene.REGISTER, PHONE, IP).success());

        assertEquals(SmsCodeVerifyResult.OK,
                service.verify(SmsScene.REGISTER, "+86 138 0013 8000", storedCode()));
    }

    // ============================== 启动守卫 ==============================

    @Test
    @DisplayName("🔴 生产 + LOG → 启动就炸：日志里躺着每个人的验证码")
    void 生产不许LOG() {
        MemberSmsCodeService service =
                service(VerificationCodeProperties.Transport.LOG, SystemEnvironmentEnum.PROD);

        IllegalStateException e = assertThrows(IllegalStateException.class, service::checkTransport);
        assertTrue(e.getMessage().contains("sms-transport"), "报错要指名道姓说是哪个配置项");
    }

    @Test
    @DisplayName("🔴 生产 + REAL + 没接厂商 → 也要启动就炸，而不是等第一个用户点「获取验证码」")
    void 生产不许没有厂商() {
        when(smsSender.available()).thenReturn(false);
        MemberSmsCodeService service =
                service(VerificationCodeProperties.Transport.REAL, SystemEnvironmentEnum.PROD);

        assertThrows(IllegalStateException.class, service::checkTransport,
                "那一刻的表现是一个 500，而没人在看日志 —— 部署时炸，人还在场");
    }

    @Test
    @DisplayName("非生产 + LOG → 只警告，正常启动")
    void 非生产可以LOG() {
        service(VerificationCodeProperties.Transport.LOG, SystemEnvironmentEnum.DEV).checkTransport();
    }

    @Test
    @DisplayName("生产 + REAL + 没厂商 + 【显式声明】了 → 放行（「只用邮箱」是合法的部署形态）")
    void 显式声明之后可以没有厂商() {
        when(smsSender.available()).thenReturn(false);
        properties.setAllowMissingSmsVendor(true);

        service(VerificationCodeProperties.Transport.REAL, SystemEnvironmentEnum.PROD)
                .checkTransport();
    }

    @Test
    @DisplayName("🔴 那个开关【只】放行「没厂商」，放行不了 LOG 通道")
    void 显式声明救不了LOG通道() {
        properties.setAllowMissingSmsVendor(true);
        MemberSmsCodeService service =
                service(VerificationCodeProperties.Transport.LOG, SystemEnvironmentEnum.PROD);

        /*
         * 两件事的性质完全不同，不能用同一个开关一起放行：
         *
         *   · 没接厂商 = 这条功能【不可用】。用户收不到码，注册不了 —— 看得见，会有人报。
         *   · LOG 通道 = 这条功能【看起来能用，但每个人的验证码都躺在日志里】。
         *     拿到日志（或 ELK 权限）的人可以接管任意账号，而且没有任何迹象。
         *
         * 一个是缺功能，一个是开后门。所以 allow-missing-sms-vendor 名字里
         * 写的是 vendor，它也只该管 vendor 这一件事。
         */
        assertThrows(IllegalStateException.class, service::checkTransport,
                "「允许没有厂商」不等于「允许把验证码打进生产日志」——" 
                        + "这个开关一旦能兼管两件事，迟早有人为了让服务起来而配上它");
    }

    @Test
    @DisplayName("默认【不】放行 —— 这个开关必须是有人主动配上去的")
    void 默认不放行() {
        assertFalse(new VerificationCodeProperties().isAllowMissingSmsVendor(),
                "默认值一旦是 true，那道启动闸就等于不存在了");
    }
}
