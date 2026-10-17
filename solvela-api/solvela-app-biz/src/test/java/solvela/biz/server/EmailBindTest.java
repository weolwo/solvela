package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
import solvela.member.api.EmailBindFailReason;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberEmailBindCmd;
import solvela.member.api.MemberEmailBindResult;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.auth.MemberAuthService;
import solvela.member.util.MemberEmailUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 绑定 / 更换邮箱。
 *
 * <h3>这套用例真正要钉住的是那条权限提升链</h3>
 * <pre>
 *   会话被盗 → 换绑成攻击者自己的邮箱 → 用「忘记密码」重置 → 永久接管账号
 * </pre>
 * 每一步单看都合法。而 C 端令牌有 30 天有效期，token 泄露的机会比密码泄露多得多 ——
 * 所以<b>换绑必须多验一道「你是原主」</b>，而首次绑定不需要（那时链条的起点还不存在）。
 *
 * @Date 2026-09-09
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ExtendWith(RelaxedRiskRules.class)
@TestPropertySource(properties = "solvela.member.risk.cache-refresh=0s")
/*
 * ⚠️ 强制 REAL 通道。test profile 默认是 LOG（省掉配 SMTP 这一步），
 * 但本类里有几条断言是「不该调用 MailService」—— LOG 模式下它本来就不会被调用，
 * 那些断言会变成永真，等于什么都没验。集成测试要跑的是【生产那条路径】。
 * MailService 本身仍是 @MockitoBean，所以不会真发信。
 */
@org.springframework.test.context.TestPropertySource(
        properties = "solvela.member.code.email-transport=REAL")
class EmailBindTest {

    private static final String PASSWORD = "SvBind2026";

    @MockitoBean
    private MailService mailService;

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

    private static String freshEmail() {
        return "bind" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
    }

    private static String freshPhone() {
        return "13" + (100_000_000 + ThreadLocalRandom.current().nextInt(800_000_000));
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }

    private String sendAndReadCode(EmailCodeScene scene, String email, Long asMember) {
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(scene, email, freshIp(), asMember)).success());
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    /** 建一个手机号会员（没有邮箱），用来验首次绑定。 */
    private void registerByPhone(String password) {
        String phone = freshPhone();
        String ip = freshIp();
        MemberRegisterResult r = memberAuthService.register(MemberRegisterCmd.byPhonePassword(
                phone, password,
                TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip),
                "APP", ip, "APP", null));
        assertTrue(r.success(), "前提不成立：" + r.reason());
        memberId = r.identity().memberId();
    }

    private MemberEmailBindResult bind(String email, String code, String password, String oldCode) {
        return memberAuthService.bindEmail(
                new MemberEmailBindCmd(memberId, email, code, password, oldCode, freshIp(), null));
    }

    private String currentEmailHash() {
        return jdbcTemplate.queryForObject(
                "SELECT HEX(email_hash) FROM t_member WHERE member_id = ?", String.class, memberId);
    }

    // ============================== 首次绑定 ==============================

    @Test
    @DisplayName("首次绑定：只验新邮箱的码就够了")
    void 首次绑定() {
        registerByPhone(PASSWORD);
        String email = freshEmail();
        String code = sendAndReadCode(EmailCodeScene.BIND, email, memberId);

        assertTrue(bind(email, code, null, null).success(), "首次绑定不该要求原主证明");

        assertEquals(piiHasher.hash(email).toUpperCase(), currentEmailHash(),
                "email_hash 没落库的话，绑完了也按邮箱登录不了");
    }

    @Test
    @DisplayName("验证码不对 → 拒绝，邮箱不变")
    void 码不对() {
        registerByPhone(PASSWORD);
        String email = freshEmail();
        String code = sendAndReadCode(EmailCodeScene.BIND, email, memberId);

        assertEquals(EmailBindFailReason.EMAIL_CODE_MISMATCH,
                bind(email, code.equals("000000") ? "111111" : "000000", null, null).reason());
        assertNull(currentEmailHash());
    }

    private static void assertNull(Object o) {
        Assertions.assertNull(o);
    }

    @Test
    @DisplayName("邮箱已被别人绑走 → EMAIL_TAKEN")
    void 被别人占了() {
        // 甲先用这个邮箱注册
        String email = freshEmail();
        MemberRegisterResult owner = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email,
                sendAndReadCode(EmailCodeScene.REGISTER, email, null), null, null, "H5", freshIp(), "H5", null));
        assertTrue(owner.success());
        Long ownerId = owner.identity().memberId();

        try {
            // 乙想绑同一个邮箱
            registerByPhone(PASSWORD);
            // 🔴 这里必须直接构造码：issuer 会因为「被别人占了」而静默不寄，
            //    拿不到码。这条用例验的是【就算拿到了码也绑不上】那一层
            String code = forceCode(EmailCodeScene.BIND, email);

            assertEquals(EmailBindFailReason.EMAIL_TAKEN, bind(email, code, null, null).reason());
        } finally {
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", ownerId);
        }
    }

    /** 绕过 issuer 直接往 Redis 里塞一个码：用于「本来不该发信」的场景。 */
    private String forceCode(EmailCodeScene scene, String email) {
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        redisService.set(key, "654321|" + System.currentTimeMillis() + "|0", 300);
        return "654321";
    }

    // ============================== 换绑 ==============================

    @Test
    @DisplayName("🔴 换绑不给原主证明 → REBIND_VERIFICATION_REQUIRED，邮箱不变")
    void 换绑必须证明原主() {
        registerByPhone(PASSWORD);
        String first = freshEmail();
        assertTrue(bind(first, sendAndReadCode(EmailCodeScene.BIND, first, memberId), null, null).success());
        String firstHash = currentEmailHash();

        String second = freshEmail();
        MemberEmailBindResult result =
                bind(second, sendAndReadCode(EmailCodeScene.BIND, second, memberId), null, null);

        assertEquals(EmailBindFailReason.REBIND_VERIFICATION_REQUIRED, result.reason(),
                "只验新邮箱就能换绑的话，偷到一个 token 就等于拿走这个账号："
                        + "换绑 → 忘记密码 → 永久接管");
        assertEquals(firstHash, currentEmailHash(), "被拒之后邮箱必须原封不动");
    }

    @Test
    @DisplayName("换绑给对当前密码 → 通过")
    void 换绑用密码() {
        registerByPhone(PASSWORD);
        String first = freshEmail();
        assertTrue(bind(first, sendAndReadCode(EmailCodeScene.BIND, first, memberId), null, null).success());

        String second = freshEmail();
        assertTrue(bind(second, sendAndReadCode(EmailCodeScene.BIND, second, memberId), PASSWORD, null).success());

        assertEquals(piiHasher.hash(second).toUpperCase(), currentEmailHash());
    }

    @Test
    @DisplayName("换绑给错密码 → REBIND_VERIFICATION_FAILED，与「没给」分得开")
    void 换绑密码错() {
        registerByPhone(PASSWORD);
        String first = freshEmail();
        assertTrue(bind(first, sendAndReadCode(EmailCodeScene.BIND, first, memberId), null, null).success());

        String second = freshEmail();
        MemberEmailBindResult result =
                bind(second, sendAndReadCode(EmailCodeScene.BIND, second, memberId), "WrongPassword9", null);

        assertEquals(EmailBindFailReason.REBIND_VERIFICATION_FAILED, result.reason(),
                "「还需要一步」和「你给的不对」是两件事：客户端据此决定弹输入框还是报错");
    }

    @Test
    @DisplayName("🔴 没设过密码的会员，用【旧邮箱验证码】换绑 —— 那是他唯一的路")
    void 无密码会员用旧邮箱码换绑() {
        String first = freshEmail();
        MemberRegisterResult r = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, first,
                sendAndReadCode(EmailCodeScene.REGISTER, first, null), null, null, "H5", freshIp(), "H5", null));
        assertTrue(r.success());
        memberId = r.identity().memberId();

        String second = freshEmail();
        String newCode = sendAndReadCode(EmailCodeScene.BIND, second, memberId);
        // 旧邮箱是自己的 → issuer 必须照常寄（判据是「被【别人】占了没有」）
        String oldCode = sendAndReadCode(EmailCodeScene.BIND, first, memberId);
        assertNotNull(oldCode, "🔴 旧邮箱是自己的，必须发得出码 —— 否则这批会员永远换不了邮箱");

        assertTrue(bind(second, newCode, null, oldCode).success());
        assertEquals(piiHasher.hash(second).toUpperCase(), currentEmailHash());
    }

    @Test
    @DisplayName("换绑成功后，新邮箱能登录、旧邮箱不能")
    void 换绑后登录身份跟着走() {
        registerByPhone(PASSWORD);
        String first = freshEmail();
        assertTrue(bind(first, sendAndReadCode(EmailCodeScene.BIND, first, memberId), null, null).success());
        String second = freshEmail();
        assertTrue(bind(second, sendAndReadCode(EmailCodeScene.BIND, second, memberId), PASSWORD, null).success());

        assertTrue(memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_PASSWORD, second, PASSWORD, null,
                "H5", freshIp(), null, true)).success(),
                "新邮箱应当能登录");
        assertFalse(memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_PASSWORD, first, PASSWORD, null,
                "H5", freshIp(), null, true)).success(),
                "旧邮箱换掉之后就不该再是这个账号的登录身份了");
    }
}
