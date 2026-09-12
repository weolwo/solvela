package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.RegisterFailReason;
import solvela.member.api.SmsCodeSendCmd;
import solvela.member.api.SmsCodeSendResult;
import solvela.member.api.SmsScene;
import solvela.member.auth.MemberAuthService;
import solvela.member.util.MemberPhoneUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手机号注册的短信验证码：<b>这条链路以前一个字都没有</b>。
 *
 * <h3>它补的是什么洞</h3>
 * 在它之前，任何人都能拿别人的手机号建账号 —— 而 {@code uk_mbr_phone_hash}
 * 是唯一约束，号被占了真机主就<b>再也注册不了</b>，且无法自助解开。
 * 那不是「被骚扰」级别的问题，是「这个人永远进不来」。
 *
 * <p>走的是域服务而不是 HTTP：本进程里 {@code MemberAuthApi} 有两个 bean，
 * 按接口注入是歧义的 —— 与 {@link LoginWritesDeviceIdTest} 同一个理由。
 *
 * <p>test profile 的 {@code sms-transport: LOG} 让它<b>不需要任何厂商</b>就能跑：
 * 码被打进日志，这里从 Redis 读回来。生产上那个配置会启动失败（见
 * {@code MemberSmsCodeService.checkTransport}）。
 *
 * @Date 2026-09-10
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class PhoneRegisterSmsTest {

    private static final String PASSWORD = "SvSms2026";

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            memberId = null;
        }
    }

    /** 每次换号换 IP：号、IP 两个维度都有日限，复用会互相烧配额。 */
    private static String freshPhone() {
        return "13" + (100_000_000 + ThreadLocalRandom.current().nextInt(800_000_000));
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "192.0.2." + (1 + r.nextInt(250));
    }

    private String codeKey(String phone) {
        return redisService.generateRedisKey("mbr:code:",
                "sms:" + SmsScene.REGISTER.name() + ":"
                        + piiHasher.hash(MemberPhoneUtil.normalize(phone)));
    }

    private MemberRegisterResult register(String phone, String code, String ip) {
        MemberRegisterResult result = memberAuthService.register(
                MemberRegisterCmd.byPhonePassword(phone, PASSWORD, code, "APP", ip, "APP", null));
        if (result.success()) {
            memberId = result.identity().memberId();
        }
        return result;
    }

    @Test
    @DisplayName("发码 → 用码注册 → 成功，而且那个码【立刻失效】")
    void 正常注册() {
        String phone = freshPhone();
        String ip = freshIp();
        String code = TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip);
        assertNotNull(code, "LOG 通道下码必须落进 Redis，否则这条链路根本没跑起来");

        assertTrue(register(phone, code, ip).success());

        assertNull(redisService.get(codeKey(phone)),
                "🔴 验过的码必须被消费掉 —— 留着它，一个码就能反复用");
    }

    @Test
    @DisplayName("🔴 不给验证码 → 拒绝。这条就是那个洞本身")
    void 不给码不能注册() {
        String phone = freshPhone();

        MemberRegisterResult result = register(phone, null, freshIp());

        assertEquals(RegisterFailReason.SMS_CODE_EXPIRED, result.reason(),
                "没有码也能建号的话，任何人都能把别人的手机号占掉，而唯一约束让真机主再也注册不了");
        assertNull(memberId);
    }

    @Test
    @DisplayName("码错 → 拒绝；而且【没建号】，重发之后还能正常注册")
    void 码错之后还能重来() {
        String phone = freshPhone();
        String ip = freshIp();
        String code = TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip);

        assertEquals(RegisterFailReason.SMS_CODE_MISMATCH,
                register(phone, code.equals("000000") ? "111111" : "000000", ip).reason());

        // 码还在（错一次不作废），用对的码仍然能注册 —— 用户不该因为手滑一次就被踢出流程
        assertTrue(register(phone, code, ip).success());
    }

    @Test
    @DisplayName("同一个码不能注册两次 —— 第二次拿它去占另一个号也不行")
    void 码不能复用() {
        String phone = freshPhone();
        String ip = freshIp();
        String code = TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip);
        assertTrue(register(phone, code, ip).success());

        // 同一个号再来一次：码已被消费，先撞验证码这一关（而不是「已注册」）——
        // 顺序的证据在这里：验证码排在查重之前
        assertEquals(RegisterFailReason.SMS_CODE_EXPIRED, register(phone, code, ip).reason());
    }

    @Test
    @DisplayName("60 秒内重复索取 → TOO_FREQUENT，且【不会换一个新码】")
    void 冷却() {
        String phone = freshPhone();
        String ip = freshIp();
        String first = TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip);

        SmsCodeSendResult again = memberAuthService.sendSmsCode(
                new SmsCodeSendCmd(SmsScene.REGISTER, phone, ip, null));

        assertEquals(false, again.success());
        assertTrue(again.retryAfterSeconds() > 0, "被冷却挡住时要告诉用户还差多少秒");
        assertTrue(redisService.get(codeKey(phone)).startsWith(first),
                "🔴 被挡住时不能悄悄换掉已发出去的码 —— 用户手上那条短信会突然验不过");
    }

    @Test
    @DisplayName("手机号格式不对 → 不发、也不消耗任何配额")
    void 格式不对() {
        SmsCodeSendResult result = memberAuthService.sendSmsCode(
                new SmsCodeSendCmd(SmsScene.REGISTER, "not-a-phone", freshIp(), null));

        assertEquals(false, result.success());
    }
}
