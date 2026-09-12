package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.auth.member.MemberAccessToken;
import solvela.auth.member.MemberSessionContext;
import solvela.auth.member.MemberTokenStore;
import solvela.base.mail.MailService;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.enums.MemberStatusEnum;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberPasswordResetCmd;
import solvela.member.api.PasswordResetType;
import solvela.member.api.MemberPasswordResetResult;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.api.PasswordResetFailReason;
import solvela.member.auth.MemberAuthService;
import solvela.member.service.MemberService;
import solvela.member.util.MemberEmailUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用邮箱验证码重置密码。
 *
 * <h3>这套用例最要紧的一条：改完密码必须把人赶出去</h3>
 * 用户点「忘记密码」的<b>最常见原因之一</b>就是「我怀疑号被人动过」。
 * 只改密码不吊销会话，攻击者手里那个 30 天有效期的令牌<b>照样能用</b> ——
 * 而用户以为自己已经把人赶出去了。这比不改密码更糟：他不会再采取任何行动。
 *
 * @Date 2026-09-09
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
/*
 * ⚠️ 强制 REAL 通道。test profile 默认是 LOG（省掉配 SMTP 这一步），
 * 但本类里有几条断言是「不该调用 MailService」—— LOG 模式下它本来就不会被调用，
 * 那些断言会变成永真，等于什么都没验。集成测试要跑的是【生产那条路径】。
 * MailService 本身仍是 @MockitoBean，所以不会真发信。
 */
@org.springframework.test.context.TestPropertySource(
        properties = "solvela.member.code.email-transport=REAL")
class PasswordResetTest {

    private static final String OLD_PASSWORD = "SvOld2026x";
    private static final String NEW_PASSWORD = "SvNew2026x";

    @MockitoBean
    private MailService mailService;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberTokenStore tokenStore;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;

    private String email;

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            tokenStore.revokeAll(memberId);
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            memberId = null;
        }
    }

    private static String freshEmail() {
        return "reset" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }

    private String sendAndReadCode(EmailCodeScene scene, String target) {
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(scene, target, freshIp(), null)).success());
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(target)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    /** 建一个有邮箱、有密码的会员。 */
    private void registerWithEmail() {
        email = freshEmail();
        MemberRegisterResult r = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, sendAndReadCode(EmailCodeScene.REGISTER, email),
                null, OLD_PASSWORD, "H5", freshIp(), "H5", null));
        assertTrue(r.success(), "前提不成立：" + r.reason());
        memberId = r.identity().memberId();
    }

    private MemberPasswordResetResult reset(String code, String newPassword) {
        return memberAuthService.resetPassword(
                new MemberPasswordResetCmd(PasswordResetType.EMAIL_CODE, email, code, newPassword, freshIp(), null));
    }

    private boolean canLogin(String password) {
        return memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_PASSWORD, email, password, null,
                "H5", freshIp(), null)).success();
    }

    // ============================== 主路径 ==============================

    @Test
    @DisplayName("🔴 重置成功：新密码能登录，旧密码不能，而且【所有会话被吊销】")
    void 重置并踢下线() {
        registerWithEmail();
        MemberAccessToken phone = tokenStore.issue(memberId, MemberSessionContext.empty());
        MemberAccessToken pad = tokenStore.issue(memberId, MemberSessionContext.empty());
        assertEquals(memberId, tokenStore.resolve(phone.value()), "前提不成立：令牌一开始就无效");

        MemberPasswordResetResult result = reset(sendAndReadCode(EmailCodeScene.RESET_PASSWORD, email), NEW_PASSWORD);

        assertTrue(result.success(), "失败原因：" + result.reason());
        assertTrue(result.revokedSessions() >= 2, "应当报告吊销了几个会话，实际：" + result.revokedSessions());
        assertNull(tokenStore.resolve(phone.value()),
                "🔴 只改密码不吊销会话的话，攻击者手里那个 30 天有效期的令牌照样能用，"
                        + "而用户以为自己已经把人赶出去了");
        assertNull(tokenStore.resolve(pad.value()), "多设备的会话必须一起吊销");
        assertTrue(canLogin(NEW_PASSWORD));
        assertFalse(canLogin(OLD_PASSWORD), "旧密码必须失效");
    }

    @Test
    @DisplayName("验证码用一次就作废 —— 拿到一次码不能反复改密码")
    void 码只能用一次() {
        registerWithEmail();
        String code = sendAndReadCode(EmailCodeScene.RESET_PASSWORD, email);

        assertTrue(reset(code, NEW_PASSWORD).success());

        assertEquals(PasswordResetFailReason.EMAIL_CODE_EXPIRED, reset(code, "SvThird2026").reason());
        assertTrue(canLogin(NEW_PASSWORD), "第二次没生效，密码应当还是第一次改的那个");
    }

    // ============================== 拒绝路径 ==============================

    @Test
    @DisplayName("验证码错 → 拒绝，密码不变")
    void 码错() {
        registerWithEmail();
        String code = sendAndReadCode(EmailCodeScene.RESET_PASSWORD, email);

        assertEquals(PasswordResetFailReason.EMAIL_CODE_MISMATCH,
                reset(code.equals("000000") ? "111111" : "000000", NEW_PASSWORD).reason());
        assertTrue(canLogin(OLD_PASSWORD), "被拒之后密码必须原封不动");
    }

    @Test
    @DisplayName("新密码太弱 → 拒绝，且【码已经被消费掉了】")
    void 密码太弱() {
        registerWithEmail();
        String code = sendAndReadCode(EmailCodeScene.RESET_PASSWORD, email);

        assertEquals(PasswordResetFailReason.WEAK_PASSWORD, reset(code, "123").reason());
        assertTrue(canLogin(OLD_PASSWORD));
        // ⚠️ 码在强度校验【之前】就被消费了，所以用户得重新获取一次。
        //    换成先校验强度也说得通，但那样一个不知道码的人就能拿这个接口反复试探密码规则
        assertEquals(PasswordResetFailReason.EMAIL_CODE_EXPIRED, reset(code, NEW_PASSWORD).reason());
    }

    @Test
    @DisplayName("🔴 冻结的账号不许自助找回 —— 否则风控封了他，他改个密码就能继续用")
    void 冻结账号不许找回() {
        registerWithEmail();
        memberService.updateStatus(memberId, MemberStatusEnum.FROZEN, "acceptance-test");

        assertEquals(PasswordResetFailReason.ACCOUNT_UNAVAILABLE,
                reset(sendAndReadCode(EmailCodeScene.RESET_PASSWORD, email), NEW_PASSWORD).reason());

        // 解冻之后旧密码仍然有效，说明刚才那次真的没改动任何东西
        memberService.updateStatus(memberId, MemberStatusEnum.NORMAL, "acceptance-test");
        assertTrue(canLogin(OLD_PASSWORD));
    }

    @Test
    @DisplayName("邮箱格式不对 → BAD_EMAIL_FORMAT")
    void 格式不对() {
        email = "not-an-email";
        assertEquals(PasswordResetFailReason.BAD_EMAIL_FORMAT, reset("123456", NEW_PASSWORD).reason());
        email = null;
    }

    @Test
    @DisplayName("🔴 没有账号的邮箱：发码照样成功，输错码的回答与有账号时一致")
    void 没有账号也不泄露() {
        String stranger = freshEmail();

        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.RESET_PASSWORD, stranger, freshIp(), null)).success());
        assertNotNull(redisService.get(redisService.generateRedisKey("mbr:code:",
                        "email:" + EmailCodeScene.RESET_PASSWORD.name() + ":" + piiHasher.hash(stranger))),
                "码必须照样存 —— 不存的话校验那一步会漏出「这个邮箱没账号」");

        // 有账号
        registerWithEmail();
        sendAndReadCode(EmailCodeScene.RESET_PASSWORD, email);
        PasswordResetFailReason withAccount = reset("000000", NEW_PASSWORD).reason();

        // 没账号
        String realEmail = email;
        email = stranger;
        PasswordResetFailReason withoutAccount = reset("000000", NEW_PASSWORD).reason();
        email = realEmail;

        assertEquals(withAccount, withoutAccount,
                "两个回答不一样的话，攻击者请求一次码、随便输个错码就能把用户枚举出来");
    }
}
