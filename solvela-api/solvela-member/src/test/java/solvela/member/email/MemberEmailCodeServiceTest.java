package solvela.member.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.mail.MailService;
import solvela.base.mail.MailTemplateCodeEnum;
import solvela.base.domain.SystemEnvironment;
import solvela.base.module.redis.RedisService;
import solvela.base.enumeration.SystemEnvironmentEnum;
import solvela.crypto.PiiHasher;
import solvela.exception.BusinessException;
import solvela.member.api.EmailCodeFailReason;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendResult;
import solvela.member.api.EmailCodeVerifyResult;
import solvela.member.code.VerificationCodeProperties;
import solvela.member.code.VerificationCodeStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * 邮箱验证码的发送与校验。
 *
 * <h3>这套用例最要紧的三条</h3>
 * <ul>
 *   <li><b>验错有次数上限</b>。6 位码只有 100 万种组合、5 分钟有效期 ——
 *       没有上限的话一个脚本几秒钟就穷举完了。管理端那套 4 位码至今没有这个上限；</li>
 *   <li><b>验错之后不能续期</b>。写回 Redis 时如果给一个完整的 TTL，
 *       攻击者只要一直猜，这个码就永远不过期，有效期形同虚设；</li>
 *   <li><b>码用一次就作废</b>。不消费的话，「重置密码」那条链路上
 *       攻击者拿到一次码就能改无数次密码。</li>
 * </ul>
 *
 * <p>{@link MailService} 是 mock —— 真发信要连 SMTP，而这套用例跑在每次构建上。
 * 「信到底长什么样」由模板负责，不是这里能验的。
 *
 * @Date 2026-09-09
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberEmailCodeServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String HASH = "abcdef0123456789";

    @Mock
    private RedisService redisService;
    @Mock
    private MailService mailService;
    @Mock
    private PiiHasher piiHasher;

    private VerificationCodeProperties properties;
    private MemberEmailCodeService service;

    /** 模拟 Redis 的一个键值对；测试只用得到一个码键。 */
    private final Map<String, String> store = new HashMap<>();
    private long ttl = 300L;

    @BeforeEach
    void setUp() {
        properties = new VerificationCodeProperties();
        SystemEnvironment env = new SystemEnvironment(false, "solvela", SystemEnvironmentEnum.DEV);
        service = new MemberEmailCodeService(new VerificationCodeStore(redisService, properties),
                mailService, piiHasher, properties, env);
        injectSyncExecutor(service);

        when(piiHasher.hash(anyString())).thenReturn(HASH);
        when(redisService.generateRedisKey(anyString(), anyString()))
                .thenAnswer(i -> i.<String>getArgument(0) + i.<String>getArgument(1));
        when(redisService.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        when(redisService.getExpire(anyString())).thenAnswer(i -> ttl);
        // 计数键默认没超
        when(redisService.increment(anyString(), anyLong())).thenReturn(1L);
    }

    /** 记录 set 进去的值，供断言与「模拟下一次读」。 */
    private void captureSets() {
        org.mockito.Mockito.doAnswer(i -> {
            store.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(redisService).set(anyString(), anyString(), anyLong());
        org.mockito.Mockito.doAnswer(i -> {
            // delete 是可变参数，Mockito 会把它摊平成若干个 String 参数
            for (Object k : i.getArguments()) {
                if (k instanceof String key) {
                    store.remove(key);
                } else if (k instanceof String[] keys) {
                    for (String key : keys) {
                        store.remove(key);
                    }
                }
            }
            return null;
        }).when(redisService).delete(anyString());
    }

    /** 发一次码并把它取出来。 */
    private String sendAndReadCode(EmailCodeScene scene) {
        captureSets();
        assertTrue(service.send(scene, EMAIL, "10.0.0.1").success());
        String stored = store.values().iterator().next();
        return stored.split("\\|")[0];
    }

    // ============================== 发送 ==============================

    @Test
    @DisplayName("发送成功：码存进 Redis，信发出去，模板与场景对得上")
    void 发送() {
        captureSets();

        assertTrue(service.send(EmailCodeScene.RESET_PASSWORD, EMAIL, "10.0.0.1").success());

        ArgumentCaptor<MailTemplateCodeEnum> tpl = ArgumentCaptor.forClass(MailTemplateCodeEnum.class);
        verify(mailService).sendMail(tpl.capture(), any(), eq(List.of(EMAIL)));
        assertEquals(MailTemplateCodeEnum.MEMBER_RESET_PASSWORD_CODE, tpl.getValue(),
                "🔴 四个场景各自一封信 —— 共用模板的话，一个为「绑定邮箱」发的码就能拿去重置密码");
    }

    @Test
    @DisplayName("验证码是 6 位数字，不是管理端那套的 4 位")
    void 码的位数() {
        String code = sendAndReadCode(EmailCodeScene.LOGIN);

        assertTrue(code.matches("\\d{6}"),
                "6 位=100 万种组合，配 5 次上限才有意义；4 位只有 1 万种。实际：" + code);
    }

    @Test
    @DisplayName("邮箱格式不对 → 直接拒，且不消耗任何配额")
    void 格式不对() {
        for (String bad : new String[]{null, "", "  ", "no-at-sign", "a@b", "@example.com", "a@"}) {
            EmailCodeSendResult r = service.send(EmailCodeScene.REGISTER, bad, "10.0.0.1");
            assertEquals(EmailCodeFailReason.BAD_EMAIL_FORMAT, r.reason(), "应当拒绝：" + bad);
        }
        verify(redisService, never()).increment(anyString(), anyLong());
        verify(mailService, never()).sendMail(any(), any(), any());
    }

    @Test
    @DisplayName("🔴 冷却期内重发 → 拒绝，并告诉还要等多久")
    void 冷却() {
        captureSets();
        service.send(EmailCodeScene.LOGIN, EMAIL, "10.0.0.1");

        EmailCodeSendResult again = service.send(EmailCodeScene.LOGIN, EMAIL, "10.0.0.1");

        assertEquals(EmailCodeFailReason.TOO_FREQUENT, again.reason());
        assertTrue(again.retryAfterSeconds() > 0, "让用户点第二次才知道被限，是投诉的主要来源");
        verify(mailService).sendMail(any(), any(), any());
    }

    @Test
    @DisplayName("当天发送超限 → DAILY_LIMIT_REACHED")
    void 日限() {
        when(redisService.increment(anyString(), anyLong()))
                .thenReturn((long) properties.getMaxSendPerTargetPerDay() + 1);

        EmailCodeSendResult r = service.send(EmailCodeScene.REGISTER, EMAIL, "10.0.0.1");

        assertEquals(EmailCodeFailReason.DAILY_LIMIT_REACHED, r.reason());
        verify(mailService, never()).sendMail(any(), any(), any());
    }

    @Test
    @DisplayName("拿不到 IP → 放行（只是少一层保护），不是拒绝")
    void 没有IP也放行() {
        captureSets();

        assertTrue(service.send(EmailCodeScene.LOGIN, EMAIL, null).success(),
                "取 IP 失败一律拒绝，会让它变成「全站收不到验证码」");
    }

    @Test
    @DisplayName("🔴 发信失败 → 把刚存的码删掉，否则用户收不到信还被冷却挡住")
    void 发信失败要清掉码() {
        captureSets();
        doThrow(new BusinessException("邮件发送失败")).when(mailService).sendMail(any(), any(), any());

        EmailCodeSendResult r = service.send(EmailCodeScene.LOGIN, EMAIL, "10.0.0.1");

        // 🔴 发信改异步之后，「失败」不再同步回给调用方：
        //    响应表示「已受理」，真正的投递在发信线程池里跑（防枚举本就要求
        //    「已受理 ≠ 已送达」，见 EmailCodeSendResult.ok()）。
        //    这里的执行器是同步的（injectSyncExecutor），所以 deliver 已经跑完、
        //    也已经把码删了 —— 被保护的行为没变，变的只是它不再体现在返回值里。
        assertTrue(r.success(), "发信失败不再同步暴露给调用方，响应仍是『已受理』");
        assertTrue(store.isEmpty(), "码没删掉的话，60 秒内用户既收不到信、也不能重发");
    }

    @Test
    @DisplayName("🔴 发信队列排满 → 当场返回失败并删码（唯一会同步暴露发送失败的路径）")
    void 队列满时同步失败并删码() {
        captureSets();
        // 换一个「一提交就拒绝」的执行器，模拟队列打满
        org.springframework.core.task.AsyncTaskExecutor rejecting = task -> {
            throw new java.util.concurrent.RejectedExecutionException("queue full");
        };
        try {
            java.lang.reflect.Field f =
                    MemberEmailCodeService.class.getDeclaredField("emailSendExecutor");
            f.setAccessible(true);
            f.set(service, rejecting);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        EmailCodeSendResult r = service.send(EmailCodeScene.LOGIN, EMAIL, "10.0.0.1");

        // 提交在请求线程上同步发生，所以「连队列都排不下」这种情况【当场可感知】，
        // 不会静默丢。此时也要删码，理由同上。
        assertEquals(EmailCodeFailReason.SEND_FAILED, r.reason());
        assertTrue(store.isEmpty(), "被拒时也要删码，否则用户被冷却挡住却什么都没收到");
    }

    // ============================== 校验 ==============================

    @Test
    @DisplayName("码对 → OK，而且【立刻作废】")
    void 校验通过并消费() {
        String code = sendAndReadCode(EmailCodeScene.RESET_PASSWORD);

        assertEquals(EmailCodeVerifyResult.OK, service.verify(EmailCodeScene.RESET_PASSWORD, EMAIL, code));
        assertEquals(EmailCodeVerifyResult.NOT_FOUND,
                service.verify(EmailCodeScene.RESET_PASSWORD, EMAIL, code),
                "🔴 一个码只能用一次 —— 否则重置密码那条链路上，拿到一次码就能改无数次密码");
    }

    @Test
    @DisplayName("码前后有空格也认 —— 用户从邮件里复制常常带空格")
    void 容忍空格() {
        String code = sendAndReadCode(EmailCodeScene.LOGIN);

        assertEquals(EmailCodeVerifyResult.OK, service.verify(EmailCodeScene.LOGIN, EMAIL, "  " + code + " "));
    }

    @Test
    @DisplayName("从没发过 / 已过期 → NOT_FOUND，两者分不出来也不该分")
    void 没有码() {
        assertEquals(EmailCodeVerifyResult.NOT_FOUND, service.verify(EmailCodeScene.LOGIN, EMAIL, "123456"));
    }

    @Test
    @DisplayName("🔴 场景不匹配 → 验不过。一个绑定邮箱的码不能拿去重置密码")
    void 场景隔离() {
        String code = sendAndReadCode(EmailCodeScene.BIND);

        assertEquals(EmailCodeVerifyResult.NOT_FOUND,
                service.verify(EmailCodeScene.RESET_PASSWORD, EMAIL, code),
                "共用的话，诱导用户走一次「绑定邮箱」拿到码，转手就能改他的密码");
    }

    @Test
    @DisplayName("🔴 连续输错到上限 → 码被作废，必须重发")
    void 失败次数上限() {
        String code = sendAndReadCode(EmailCodeScene.LOGIN);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 1; i < properties.maxVerifyAttempts(); i++) {
            assertEquals(EmailCodeVerifyResult.MISMATCH, service.verify(EmailCodeScene.LOGIN, EMAIL, wrong),
                    "第 " + i + " 次错应当只是 MISMATCH");
        }
        assertEquals(EmailCodeVerifyResult.TOO_MANY_ATTEMPTS,
                service.verify(EmailCodeScene.LOGIN, EMAIL, wrong));

        // 作废之后，连正确的码也不认了
        assertEquals(EmailCodeVerifyResult.NOT_FOUND, service.verify(EmailCodeScene.LOGIN, EMAIL, code),
                "6 位码只有 100 万种组合，没有次数上限的话脚本几秒钟就穷举完了");
    }

    @Test
    @DisplayName("🔴 验错写回时必须保住【剩余】有效期，不能续成完整 TTL")
    void 验错不续期() {
        sendAndReadCode(EmailCodeScene.LOGIN);
        ttl = 42L;

        service.verify(EmailCodeScene.LOGIN, EMAIL, "000000");

        ArgumentCaptor<Long> written = ArgumentCaptor.forClass(Long.class);
        verify(redisService, org.mockito.Mockito.atLeast(2))
                .set(anyString(), anyString(), written.capture());
        assertEquals(42L, written.getValue(),
                "写回时给完整 TTL 的话，攻击者只要一直猜，这个码就永远不过期");
    }

    @Test
    @DisplayName("验错时剩余有效期已经没了 → NOT_FOUND，不是 MISMATCH")
    void 验错时恰好过期() {
        sendAndReadCode(EmailCodeScene.LOGIN);
        ttl = -2L;

        assertEquals(EmailCodeVerifyResult.NOT_FOUND, service.verify(EmailCodeScene.LOGIN, EMAIL, "000000"));
    }

    @Test
    @DisplayName("Redis 里的值被人手改坏 → 当成没有，并清掉，不抛")
    void 值坏掉() {
        captureSets();
        service.send(EmailCodeScene.LOGIN, EMAIL, "10.0.0.1");
        String key = store.keySet().iterator().next();
        store.put(key, "被手改坏的值");

        assertEquals(EmailCodeVerifyResult.NOT_FOUND, service.verify(EmailCodeScene.LOGIN, EMAIL, "123456"));
        assertTrue(store.isEmpty(), "坏值要顺手清掉，否则这个邮箱在有效期内一直验不过");
    }

    @Test
    @DisplayName("邮箱规范化：域名大小写不影响，本地部分【保留】大小写")
    void 规范化() {
        captureSets();
        assertTrue(service.send(EmailCodeScene.LOGIN, "User@EXAMPLE.com", "10.0.0.1").success());

        ArgumentCaptor<List<String>> to = ArgumentCaptor.captor();
        verify(mailService).sendMail(any(), any(), to.capture());
        assertEquals("User@example.com", to.getValue().get(0),
                "🔴 本地部分不能转小写：是否区分大小写由收件服务器决定，"
                        + "统一转小写会在某些服务器上把验证码发给【别人】");
        assertNotNull(to.getValue());
    }

    @Test
    @DisplayName("发送与校验用同一个键 —— 三处拼接不一致的话，码发得出去但怎么填都说失效")
    void 键一致() {
        String code = sendAndReadCode(EmailCodeScene.REGISTER);

        assertEquals(EmailCodeVerifyResult.OK, service.verify(EmailCodeScene.REGISTER, EMAIL, code));
    }

    @Test
    @DisplayName("邮箱明文不进 Redis 键 —— dump 出来不该是一串真实地址")
    void 键里不含明文邮箱() {
        captureSets();
        service.send(EmailCodeScene.LOGIN, EMAIL, "10.0.0.1");

        String key = store.keySet().iterator().next();
        assertFalse(key.contains(EMAIL), "键里出现了明文邮箱：" + key);
        assertTrue(key.contains(HASH), "应当用 PiiHasher 的摘要，与 t_member.email_hash 同一个值");
    }

    // ============================== LOG 通道 ==============================

    /** 换一个通道 / 环境重建 service。 */
    private MemberEmailCodeService serviceWith(VerificationCodeProperties.Transport transport, boolean prod) {
        properties.setEmailTransport(transport);
        SystemEnvironment env = new SystemEnvironment(prod, "solvela",
                prod ? SystemEnvironmentEnum.PROD : SystemEnvironmentEnum.DEV);
        MemberEmailCodeService s = new MemberEmailCodeService(new VerificationCodeStore(redisService, properties),
                mailService, piiHasher, properties, env);
        injectSyncExecutor(s);
        s.checkTransport();
        return s;
    }

    @Test
    @DisplayName("LOG 通道：不调 MailService，但码【照样存】—— 否则校验就过不了")
    void log通道不发信但存码() {
        captureSets();
        MemberEmailCodeService logService = serviceWith(VerificationCodeProperties.Transport.LOG, false);

        assertTrue(logService.send(EmailCodeScene.LOGIN, EMAIL, "10.0.0.1").success());

        verify(mailService, never()).sendMail(any(), any(), any());
        assertFalse(store.isEmpty(), "码没存的话，从日志里抄出来也验不过");
        assertEquals(EmailCodeVerifyResult.OK,
                logService.verify(EmailCodeScene.LOGIN, EMAIL, store.values().iterator().next().split("\\|")[0]),
                "日志里那个码必须真的能用 —— 这是这个通道的全部意义");
    }

    @Test
    @DisplayName("🔴 生产环境 + LOG 通道 → 启动即失败，不是静默降级")
    void 生产不许用log通道() {
        IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> serviceWith(VerificationCodeProperties.Transport.LOG, true));

        assertTrue(e.getMessage().contains("transport=LOG"), "报错要说清是哪个配置项，实际：" + e.getMessage());
        // 静默降级成 MAIL 的话，有人在生产配了 LOG 却什么都没发生，
        // 他会以为这个开关不生效、转头去别处找原因，而真正的问题没人知道
    }

    @Test
    @DisplayName("生产环境 + MAIL 通道 → 正常")
    void 生产用real通道() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> serviceWith(VerificationCodeProperties.Transport.REAL, true));
    }

    @Test
    @DisplayName("默认是 REAL —— 新的调试开关不该默认生效")
    void 默认real() {
        assertEquals(VerificationCodeProperties.Transport.REAL, new VerificationCodeProperties().getEmailTransport());
    }

    /**
     * 生产上发信走专用线程池（EmailSendExecutorConfig），字段注入。
     * 测试里塞一个【同步】执行器：Runnable 当场在本线程跑完 ——
     * 这样「信有没有发出去」「发失败有没有把码删掉」这些断言不用去等异步，
     * 而被验证的逻辑（提交/拒绝/删码）与生产完全一致。
     */
    private static void injectSyncExecutor(MemberEmailCodeService target) {
        org.springframework.core.task.AsyncTaskExecutor sync =
                new org.springframework.core.task.support.TaskExecutorAdapter(Runnable::run);
        try {
            java.lang.reflect.Field f =
                    MemberEmailCodeService.class.getDeclaredField("emailSendExecutor");
            f.setAccessible(true);
            f.set(target, sync);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入同步执行器失败", e);
        }
    }
}
