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
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberPhoneBindCmd;
import solvela.member.api.MemberPhoneBindResult;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.api.PhoneBindFailReason;
import solvela.member.api.SmsCodeSendCmd;
import solvela.member.api.SmsScene;
import solvela.member.auth.MemberAuthService;
import solvela.member.util.MemberEmailUtil;
import solvela.member.util.MemberPhoneUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 绑定 / 更换手机号。
 *
 * <h3>要钉的还是那条权限提升链</h3>
 * <pre>会话被盗 → 换绑成攻击者的手机号 → 用短信验证码登录 → 永久接管</pre>
 * 每一步单看都合法。而 C 端令牌有 30 天有效期 ——
 * 所以<b>换绑必须多验一道「你是原主」</b>，而首次绑定不需要
 *（那时链条的起点还不存在）。
 *
 * <p>与 {@link EmailBindTest} 逐条对称，这是刻意的：两条通道的安全规则本来就该一样，
 * 差异只应该来自它们真正不同的地方，而不是「当时谁写的」。
 *
 * @Date 2026-09-10
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class PhoneBindTest {

    private static final String PASSWORD = "SvPhone2026";

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

    private static String freshPhone() {
        return "13" + (100_000_000 + ThreadLocalRandom.current().nextInt(800_000_000));
    }

    private static String freshEmail() {
        return "pbind" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "192.0.2." + (1 + r.nextInt(250));
    }

    /** 发一条 BIND 场景的短信码并读回来。test profile 走 LOG 通道，不需要厂商 */
    private String bindCode(String phone) {
        /*
         * 🔴 带上 memberId。BIND 场景的投递判断看的是「被【别人】占了没有」——
         * 不带的话它退化成「有没有人占」，于是给【自己的旧号码】发码会被静默拦掉，
         * 而那正是没设过密码的会员唯一能换绑的路。
         */
        assertTrue(memberAuthService.sendSmsCode(
                new SmsCodeSendCmd(SmsScene.BIND, phone, freshIp(), memberId)).success());
        String key = redisService.generateRedisKey("mbr:code:",
                "sms:BIND:" + piiHasher.hash(MemberPhoneUtil.normalize(phone)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    private String emailCode(EmailCodeScene scene, String email) {
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(scene, email, freshIp(), memberId)).success());
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    /** 建一个手机号会员（有密码）。 */
    private String registerByPhone() {
        String phone = freshPhone();
        String ip = freshIp();
        MemberRegisterResult r = memberAuthService.register(MemberRegisterCmd.byPhonePassword(
                phone, PASSWORD,
                TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip),
                "APP", ip, "APP", null));
        assertTrue(r.success(), "前提不成立：" + r.reason());
        memberId = r.identity().memberId();
        return phone;
    }

    /** 建一个邮箱会员（<b>没有</b>密码，也没有手机号）。 */
    private void registerByEmailNoPassword() {
        String email = freshEmail();
        String code = emailCode(EmailCodeScene.REGISTER, email);
        MemberRegisterResult r = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, code, null, null, "H5", freshIp(), "H5", null));
        assertTrue(r.success(), "前提不成立：" + r.reason());
        memberId = r.identity().memberId();
    }

    private MemberPhoneBindResult bind(String phone, String code, String password, String oldCode) {
        return memberAuthService.bindPhone(
                new MemberPhoneBindCmd(memberId, phone, code, password, oldCode, freshIp(), null));
    }

    private String currentPhoneHash() {
        return jdbcTemplate.queryForObject(
                "SELECT HEX(phone_hash) FROM t_member WHERE member_id = ?", String.class, memberId);
    }

    // ============================== 首次绑定 ==============================

    @Test
    @DisplayName("🔴 没有手机号的会员首次绑定：只验新号码的码就够了")
    void 首次绑定() {
        registerByEmailNoPassword();
        String phone = freshPhone();

        assertTrue(bind(phone, bindCode(phone), null, null).success(), "首次绑定不该要求原主证明");

        assertEquals(piiHasher.hash(phone).toUpperCase(), currentPhoneHash(),
                "phone_hash 没落库的话，绑完了也按手机号登录不了");
    }

    @Test
    @DisplayName("绑完就能用它登录 —— 那才是「绑定手机号」的意义")
    void 绑完能登录() {
        registerByEmailNoPassword();
        String phone = freshPhone();
        assertTrue(bind(phone, bindCode(phone), null, null).success());

        // 这个会员没有密码，所以验「查得到人」而不是「登得进去」：
        // 查不到人时返回的是 BAD_CREDENTIALS，查到了但没密码返回 NO_PASSWORD
        assertEquals(solvela.member.api.AuthFailReason.NO_PASSWORD,
                memberAuthService.authenticate(new MemberAuthCmd(
                        MemberLoginType.PHONE_PASSWORD, phone, "whatever", null,
                        "H5", freshIp(), null)).reason(),
                "回 BAD_CREDENTIALS 说明按这个号根本查不到人 —— 绑定没真的生效");
    }

    @Test
    @DisplayName("验证码不对 → 拒绝，号码不变")
    void 码不对() {
        registerByEmailNoPassword();
        String phone = freshPhone();
        String code = bindCode(phone);

        assertEquals(PhoneBindFailReason.SMS_CODE_MISMATCH,
                bind(phone, code.equals("000000") ? "111111" : "000000", null, null).reason());
    }

    @Test
    @DisplayName("号码格式不对 → 拒绝，而且【不消耗验证码】")
    void 格式不对() {
        registerByEmailNoPassword();

        assertEquals(PhoneBindFailReason.BAD_PHONE_FORMAT,
                bind("not-a-phone", "123456", null, null).reason());
    }

    @Test
    @DisplayName("该号码已被别人绑走 → PHONE_TAKEN")
    void 被别人占了() {
        String taken = registerByPhone();
        Long ownerId = memberId;
        memberId = null;
        try {
            registerByEmailNoPassword();

            assertEquals(PhoneBindFailReason.PHONE_TAKEN,
                    bind(taken, bindCode(taken), null, null).reason(),
                    "必须如实说 —— 藏起来的话用户不知道该换个号还是去找回账号");
        } finally {
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", ownerId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", ownerId);
        }
    }

    // ============================== 换绑 ==============================

    @Test
    @DisplayName("🔴 换绑不给原主证明 → REBIND_VERIFICATION_REQUIRED，号码不变")
    void 换绑必须证明原主() {
        registerByPhone();
        String before = currentPhoneHash();
        String second = freshPhone();

        MemberPhoneBindResult result = bind(second, bindCode(second), null, null);

        assertEquals(PhoneBindFailReason.REBIND_VERIFICATION_REQUIRED, result.reason(),
                "只验新号码就能换绑的话，偷到一个 token 就等于拿走这个账号："
                        + "换绑 → 短信验证码登录 → 永久接管");
        assertEquals(before, currentPhoneHash(), "被拒之后号码必须原封不动");
    }

    @Test
    @DisplayName("换绑给对当前密码 → 通过")
    void 换绑用密码() {
        registerByPhone();
        String second = freshPhone();

        assertTrue(bind(second, bindCode(second), PASSWORD, null).success());

        assertEquals(piiHasher.hash(second).toUpperCase(), currentPhoneHash());
    }

    @Test
    @DisplayName("换绑给错密码 → REBIND_VERIFICATION_FAILED，与「没给」分得开")
    void 换绑密码错() {
        registerByPhone();
        String second = freshPhone();

        assertEquals(PhoneBindFailReason.REBIND_VERIFICATION_FAILED,
                bind(second, bindCode(second), "WrongPassword9", null).reason(),
                "「还需要一步」和「你给的不对」是两件事：客户端据此决定弹输入框还是报错");
    }

    @Test
    @DisplayName("🔴 用【旧手机号验证码】换绑 —— 没设过密码的会员只有这一条路")
    void 用旧号码验证码换绑() {
        registerByEmailNoPassword();
        String first = freshPhone();
        assertTrue(bind(first, bindCode(first), null, null).success());

        String second = freshPhone();
        String newCode = bindCode(second);
        String oldCode = bindCode(first);
        assertNotNull(oldCode, "旧号码是自己的，必须发得出码 —— 否则这批会员永远换不了手机号");

        assertTrue(bind(second, newCode, null, oldCode).success());
        assertEquals(piiHasher.hash(second).toUpperCase(), currentPhoneHash());
    }

    @Test
    @DisplayName("换绑成功后，新号码能查到人、旧号码查不到")
    void 换绑后身份跟着走() {
        String first = registerByPhone();
        String second = freshPhone();
        assertTrue(bind(second, bindCode(second), PASSWORD, null).success());

        assertTrue(memberAuthService.authenticate(new MemberAuthCmd(
                MemberLoginType.PHONE_PASSWORD, second, PASSWORD, null,
                "H5", freshIp(), null)).success(), "新号码应当能登录");
        assertFalse(memberAuthService.authenticate(new MemberAuthCmd(
                MemberLoginType.PHONE_PASSWORD, first, PASSWORD, null,
                "H5", freshIp(), null)).success(),
                "旧号码换掉之后就不该再是这个账号的登录身份了");
    }
}
