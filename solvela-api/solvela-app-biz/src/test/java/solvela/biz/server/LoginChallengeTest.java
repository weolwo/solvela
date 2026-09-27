package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.mail.MailService;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.enums.DeviceStatusEnum;
import solvela.member.api.AuthFailReason;
import solvela.member.api.DeviceRegisterCmd;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.LoginChallengeCmd;
import solvela.member.api.LoginChallengeCodeResult;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.auth.MemberAuthService;
import solvela.member.device.DeviceDispositionService;
import solvela.member.device.DeviceService;
import solvela.member.util.MemberEmailUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 观察档设备的登录二次验证（凭票版）端到端：真库、真 Redis，只把发信 mock 掉。
 *
 * <p>这正是旧实现里走不通的那条路：<b>邮箱 + 密码</b>登录的用户在观察档设备上，
 * 以前前端只会去匿名接口要短信码，永远过不去。现在码发到哪由票决定。
 *
 * @Date 2026-09-27
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(properties = "solvela.member.code.email-transport=REAL")
class LoginChallengeTest {

    private static final String PASSWORD = "SvChallenge2026";

    @MockitoBean
    private MailService mailService;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceDispositionService dispositionService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;

    private String email;

    private String deviceId;

    @BeforeEach
    void setUp() {
        email = "svlc" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
        String ip = freshIp();
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.REGISTER, email, ip, null)).success());
        MemberRegisterResult registered = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, readCode(EmailCodeScene.REGISTER), null, PASSWORD,
                "H5", ip, "H5", null));
        assertTrue(registered.success(), "造数：注册应当成功，实际 " + registered.reason());
        memberId = registered.identity().memberId();

        deviceId = deviceService.register(new DeviceRegisterCmd("H5", null, null, null, freshIp())).deviceId();
        assertTrue(dispositionService.disposeManually(deviceId, DeviceStatusEnum.OBSERVE, "测试：观察档", "test"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM t_device WHERE device_id = ?", deviceId);
        redisService.delete(redisService.generateRedisKey("dev:observe:", deviceId));
    }

    @Test
    @DisplayName("🔴 邮箱+密码 @ 观察档设备：拿票 → 凭票发码到【登录邮箱】→ 凭票验码登录成功，票随即作废")
    void 凭票走通() {
        MemberAuthResult first = login();
        assertEquals(AuthFailReason.DEVICE_VERIFICATION_REQUIRED, first.reason());
        String ticket = first.challengeTicket();
        assertNotNull(ticket, "要有票，否则客户端没法进行下一步");

        LoginChallengeCodeResult sent = memberAuthService.sendChallengeCode(new LoginChallengeCmd(ticket, null, freshIp(), deviceId));
        assertTrue(sent.success(), "实际 " + sent.reason());
        assertEquals(MemberEmailUtil.mask(MemberEmailUtil.normalize(email)), sent.maskedTarget(),
                "码发到登录用的那个邮箱 —— 以前这里只会去要短信码，邮箱用户永远过不去");

        String code = readCode(EmailCodeScene.LOGIN);
        MemberAuthResult done = memberAuthService.verifyChallenge(new LoginChallengeCmd(ticket, code, freshIp(), deviceId));
        assertTrue(done.success(), "实际 " + done.reason());
        assertEquals(memberId, done.identity().memberId());

        assertEquals(AuthFailReason.CHALLENGE_EXPIRED,
                memberAuthService.verifyChallenge(new LoginChallengeCmd(ticket, code, freshIp(), deviceId)).reason(),
                "票是一次性的：用过之后再拿来就是废票");
    }

    @Test
    @DisplayName("🔴 票拿到别的设备上：发码、验码都不认")
    void 换设备不认() {
        String ticket = login().challengeTicket();
        String otherDevice = "f".repeat(32);

        assertEquals(LoginChallengeCodeResult.Reason.CHALLENGE_EXPIRED,
                memberAuthService.sendChallengeCode(new LoginChallengeCmd(ticket, null, freshIp(), otherDevice)).reason());
        assertEquals(AuthFailReason.CHALLENGE_EXPIRED,
                memberAuthService.verifyChallenge(new LoginChallengeCmd(ticket, "123456", freshIp(), otherDevice)).reason());
    }

    @Test
    @DisplayName("码错 → DEVICE_VERIFICATION_FAILED，票还在，能再试")
    void 码错还能再试() {
        String ticket = login().challengeTicket();
        assertTrue(memberAuthService.sendChallengeCode(new LoginChallengeCmd(ticket, null, freshIp(), deviceId)).success());
        String code = readCode(EmailCodeScene.LOGIN);
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertEquals(AuthFailReason.DEVICE_VERIFICATION_FAILED,
                memberAuthService.verifyChallenge(new LoginChallengeCmd(ticket, wrong, freshIp(), deviceId)).reason());
        assertTrue(memberAuthService.verifyChallenge(new LoginChallengeCmd(ticket, code, freshIp(), deviceId)).success());
    }

    @Test
    @DisplayName("密码错时不签票 —— 不知道密码就拿不到票，拿不到票就发不了码")
    void 密码错不签票() {
        MemberAuthResult result = memberAuthService.authenticate(new MemberAuthCmd(
                MemberLoginType.EMAIL_PASSWORD, email, "WrongPass2026", null, "H5", freshIp(), deviceId));

        assertEquals(AuthFailReason.BAD_CREDENTIALS, result.reason());
        assertFalse(result.challengeTicket() != null);
    }

    // ============================== 工具 ==============================

    private MemberAuthResult login() {
        return memberAuthService.authenticate(new MemberAuthCmd(
                MemberLoginType.EMAIL_PASSWORD, email, PASSWORD, null, "H5", freshIp(), deviceId));
    }

    private String readCode(EmailCodeScene scene) {
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        String stored = redisService.get(key);
        assertNotNull(stored, "发码之后 Redis 里应当有一份（场景 " + scene + "）");
        return stored.split("\\|")[0];
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }
}
