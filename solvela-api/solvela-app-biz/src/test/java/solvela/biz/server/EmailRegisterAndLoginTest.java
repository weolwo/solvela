package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.mail.MailService;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.member.api.AuthFailReason;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.api.RegisterFailReason;
import solvela.member.auth.MemberAuthService;
import solvela.member.util.MemberEmailUtil;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 邮箱注册与邮箱登录的端到端验收：连真实库、真实 Redis，只把发信那一步换成 mock。
 *
 * <h3>为什么 mock 掉 MailService</h3>
 * 真发信要连 SMTP，而这套用例跑在每次构建上。验证码本身<b>是从 Redis 里读出来的</b> ——
 * 那正是服务端真实存下的那一份，所以「发出去的码和验得过的码是不是同一个」照样被验到了。
 * 信长什么样由模板负责，不是这里能验的。
 *
 * <h3>这套用例最要紧的两条</h3>
 * <ul>
 *   <li><b>邮箱注册是本项目第一条被验证过的通道</b>：没有验证码就注册不了，
 *       而手机号那条至今任何人都能拿别人的号建账号；</li>
 *   <li><b>账号是否存在不能从接口行为里推出来</b>：没有会员的邮箱照样存码、
 *       照样返回成功，输错码得到的回答与有会员时逐字相同。</li>
 * </ul>
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
class EmailRegisterAndLoginTest {

    private static final String PASSWORD = "SvEmail2026";

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

    private String email;

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            memberId = null;
        }
    }

    /** 每条用例一个新邮箱、新 IP —— 两个维度都有限频，复用会互相烧配额。 */
    private static String freshEmail() {
        return "sv" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }

    /** 发一次码并把它从 Redis 里读出来 —— 就是服务端真实存下的那一份。 */
    private String sendAndReadCode(EmailCodeScene scene, String targetEmail) {
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(scene, targetEmail, freshIp(), null)).success());
        return readCode(scene, targetEmail);
    }

    private String readCode(EmailCodeScene scene, String targetEmail) {
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(targetEmail)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    private MemberRegisterResult register(String code, String password) {
        MemberRegisterResult result = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, code, null, password, "H5", freshIp(), "H5", null));
        if (result.success()) {
            memberId = result.identity().memberId();
        }
        return result;
    }

    // ============================== 注册 ==============================

    @Test
    @DisplayName("🔴 邮箱注册：验码通过才建号，且不需要密码")
    void 邮箱注册免密() {
        email = freshEmail();
        String code = sendAndReadCode(EmailCodeScene.REGISTER, email);
        assertNotNull(code, "发码之后 Redis 里应当有一份");

        assertTrue(register(code, null).success(), "验码通过就该建号");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT phone_hash, email_hash, password FROM t_member WHERE member_id = ?", memberId);
        assertNotNull(row.get("email_hash"), "email_hash 必须落库，否则之后按邮箱查不到人");
        assertNull(row.get("phone_hash"), "邮箱注册的会员没有手机号，这一列就该是 NULL");
        assertNull(row.get("password"), "不填密码时 password 就该是 NULL —— DDL 给验证码登录留的口子");
    }

    @Test
    @DisplayName("🔴 没有验证码就注册不了 —— 这是手机号那条通道至今没有的东西")
    void 没有码注册不了() {
        email = freshEmail();

        assertEquals(RegisterFailReason.EMAIL_CODE_EXPIRED, register("123456", PASSWORD).reason(),
                "没发过码就该是「已失效」，而不是建号成功");
        assertEquals(0, countByEmail(), "不该留下任何一行");
    }

    @Test
    @DisplayName("验证码错 → 拒绝，且不建号")
    void 码错() {
        email = freshEmail();
        String code = sendAndReadCode(EmailCodeScene.REGISTER, email);

        assertEquals(RegisterFailReason.EMAIL_CODE_MISMATCH,
                register(code.equals("000000") ? "111111" : "000000", PASSWORD).reason());
        assertEquals(0, countByEmail());
    }

    @Test
    @DisplayName("邮箱注册可以设密码，设了就得过强度校验")
    void 带密码注册() {
        email = freshEmail();
        String code = sendAndReadCode(EmailCodeScene.REGISTER, email);

        assertEquals(RegisterFailReason.WEAK_PASSWORD, register(code, "123").reason(),
                "填了弱密码却被静默接受，比不让填更糟");
    }

    @Test
    @DisplayName("🔴 已注册的邮箱要码 → 静默成功【但不寄信】")
    void 已注册的邮箱不寄注册码() {
        email = freshEmail();
        assertTrue(register(sendAndReadCode(EmailCodeScene.REGISTER, email), null).success());
        Mockito.clearInvocations(mailService);

        assertTrue(memberAuthService.sendEmailCode(
                        new EmailCodeSendCmd(EmailCodeScene.REGISTER, email, freshIp(), null)).success(),
                "如实回答「已注册」等于送出一个账号枚举接口");
        verify(mailService, never()).sendMail(any(), any(), any());
    }

    // ============================== 登录 ==============================

    @Test
    @DisplayName("邮箱 + 验证码登录")
    void 验证码登录() {
        email = freshEmail();
        assertTrue(register(sendAndReadCode(EmailCodeScene.REGISTER, email), null).success());

        String loginCode = sendAndReadCode(EmailCodeScene.LOGIN, email);
        MemberAuthResult result = memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_CODE, email, loginCode, null,
                "H5", freshIp(), null, true));

        assertTrue(result.success(), "失败原因：" + result.reason());
        assertEquals(memberId, result.identity().memberId());
    }

    @Test
    @DisplayName("邮箱 + 密码登录")
    void 密码登录() {
        email = freshEmail();
        assertTrue(register(sendAndReadCode(EmailCodeScene.REGISTER, email), PASSWORD).success());

        MemberAuthResult result = memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_PASSWORD, email, PASSWORD, null,
                "H5", freshIp(), null, true));

        assertTrue(result.success(), "失败原因：" + result.reason());
        assertEquals(memberId, result.identity().memberId());
    }

    @Test
    @DisplayName("🔴 验证码用一次就作废 —— 同一个码不能登两次")
    void 码只能用一次() {
        email = freshEmail();
        assertTrue(register(sendAndReadCode(EmailCodeScene.REGISTER, email), null).success());
        String code = sendAndReadCode(EmailCodeScene.LOGIN, email);

        assertTrue(memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_CODE, email, code, null,
                "H5", freshIp(), null, true)).success());

        MemberAuthResult again = memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_CODE, email, code, null,
                "H5", freshIp(), null, true));
        assertFalse(again.success(), "码不作废的话，拿到一次就能反复用");
        assertEquals(AuthFailReason.EMAIL_CODE_EXPIRED, again.reason());
    }

    @Test
    @DisplayName("🔴 没有账号的邮箱：照样存码、照样成功，只是不寄信")
    void 没有账号也存码() {
        String stranger = freshEmail();
        Mockito.clearInvocations(mailService);

        assertTrue(memberAuthService.sendEmailCode(
                        new EmailCodeSendCmd(EmailCodeScene.LOGIN, stranger, freshIp(), null)).success(),
                "如实回答「查无此人」等于送出一个账号枚举接口");
        verify(mailService, never()).sendMail(any(), any(), any());
        assertNotNull(readCode(EmailCodeScene.LOGIN, stranger),
                "🔴 码必须照样存 —— 不存的话，校验那一步会把发送这一步藏住的东西漏出去");
    }

    @Test
    @DisplayName("🔴 有账号与没账号，输错码得到的回答【逐字相同】")
    void 输错码的回答一致() {
        // 有账号
        email = freshEmail();
        assertTrue(register(sendAndReadCode(EmailCodeScene.REGISTER, email), null).success());
        sendAndReadCode(EmailCodeScene.LOGIN, email);
        AuthFailReason withAccount = memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_CODE, email, "000000", null,
                "H5", freshIp(), null, true)).reason();

        // 没账号
        String stranger = freshEmail();
        memberAuthService.sendEmailCode(new EmailCodeSendCmd(EmailCodeScene.LOGIN, stranger, freshIp(), null));
        AuthFailReason withoutAccount = memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_CODE, stranger, "000000", null,
                "H5", freshIp(), null, true)).reason();

        assertEquals(withAccount, withoutAccount,
                "两个回答不一样的话，攻击者请求一次码、随便输个错码，就能把用户枚举出来");
    }

    @Test
    @DisplayName("邮箱格式不对 → BAD_EMAIL_FORMAT，不是「查无此人」")
    void 格式不对() {
        MemberAuthResult result = memberAuthService.authenticate(new MemberAuthCmd(MemberLoginType.EMAIL_CODE, "not-an-email", "123456", null,
                "H5", freshIp(), null, true));

        assertEquals(AuthFailReason.BAD_EMAIL_FORMAT, result.reason(),
                "含糊成「查无此人」会让用户对着一个填错了的地址反复重试");
    }

    @Test
    @DisplayName("手机号那条通道没受影响 —— 不传 loginType 时仍按它兜底")
    void 手机号通道不受影响() {
        String phone = "13" + (100_000_000 + ThreadLocalRandom.current().nextInt(800_000_000));
        String ip = freshIp();
        MemberRegisterResult reg = memberAuthService.register(MemberRegisterCmd.byPhonePassword(
                phone, PASSWORD,
                TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip),
                "APP", ip, "APP", null));
        assertTrue(reg.success());
        memberId = reg.identity().memberId();

        // loginType 传 null：老调用点的形状
        assertTrue(memberAuthService.authenticate(new MemberAuthCmd(
                null, phone, PASSWORD, null, "APP", freshIp(), null, true)).success());
    }

    private int countByEmail() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_member WHERE email_hash = UNHEX(?)",
                Integer.class, piiHasher.hash(MemberEmailUtil.normalize(email)));
        return n == null ? 0 : n;
    }
}
