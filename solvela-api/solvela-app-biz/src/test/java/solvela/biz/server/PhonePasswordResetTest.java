package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.auth.member.MemberAccessToken;
import solvela.auth.member.MemberSessionContext;
import solvela.auth.member.MemberTokenStore;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberPasswordResetCmd;
import solvela.member.api.MemberPasswordResetResult;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.PasswordResetFailReason;
import solvela.member.api.PasswordResetType;
import solvela.member.api.SmsCodeSendCmd;
import solvela.member.api.SmsScene;
import solvela.member.auth.MemberAuthService;
import solvela.member.util.MemberPhoneUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用<b>短信验证码</b>重置密码。
 *
 * <h3>为什么手机号这条也得有</h3>
 * 在它之前，找回密码只认邮箱 —— 而手机号是这个系统<b>注册的默认身份</b>。
 * 也就是说：绝大多数用户注册完、没绑邮箱，就永远没有自助找回的路，
 * 密码一忘只能找客服。
 *
 * <h3>🔴 这条链路的验证码威力最大</h3>
 * 拿到它就能改密码，等于账号易主。所以成功之后要吊销<b>全部会话</b> ——
 * 不吊销的话，攻击者手里那个 30 天有效期的令牌照样能用，
 * 而用户以为自己已经把人赶出去了。
 *
 * <p>与 {@link PasswordResetTest} 逐条对称：两条通道的规则本来就该一样。
 *
 * @Date 2026-09-10
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class PhonePasswordResetTest {

    private static final String OLD_PASSWORD = "SvOldPwd2026";
    private static final String NEW_PASSWORD = "SvNewPwd2026";

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private MemberTokenStore tokenStore;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;

    private String phone;

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            tokenStore.revokeAll(memberId);
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            memberId = null;
        }
    }

    private static String freshPhone() {
        return "13" + (100_000_000 + ThreadLocalRandom.current().nextInt(800_000_000));
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198.51." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }

    /** 发一条重置码并读回来。test profile 走 LOG 通道，不需要厂商 */
    private String resetCode(String target) {
        assertTrue(memberAuthService.sendSmsCode(
                new SmsCodeSendCmd(SmsScene.RESET_PASSWORD, target, freshIp(), null)).success());
        String key = redisService.generateRedisKey("mbr:code:",
                "sms:RESET_PASSWORD:" + piiHasher.hash(MemberPhoneUtil.normalize(target)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    private void registerByPhone() {
        phone = freshPhone();
        String ip = freshIp();
        MemberRegisterResult r = memberAuthService.register(MemberRegisterCmd.byPhonePassword(
                phone, OLD_PASSWORD,
                TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip),
                "APP", ip, "APP", null));
        assertTrue(r.success(), "前提不成立：" + r.reason());
        memberId = r.identity().memberId();
    }

    private MemberPasswordResetResult reset(String code, String newPassword) {
        return memberAuthService.resetPassword(new MemberPasswordResetCmd(
                PasswordResetType.SMS_CODE, phone, code, newPassword, freshIp(), null));
    }

    private boolean canLogin(String password) {
        return memberAuthService.authenticate(new MemberAuthCmd(
                MemberLoginType.PHONE_PASSWORD, phone, password, null,
                "H5", freshIp(), null)).success();
    }

    @Test
    @DisplayName("发码 → 重置 → 新密码能登、旧密码不能")
    void 正常重置() {
        registerByPhone();
        String code = resetCode(phone);
        assertNotNull(code, "注册过的号码必须发得出重置码");

        assertTrue(reset(code, NEW_PASSWORD).success());

        assertTrue(canLogin(NEW_PASSWORD), "新密码登不了，等于这次重置什么都没干成");
        assertFalse(canLogin(OLD_PASSWORD), "旧密码还能登的话，把号找回来这件事就没发生");
    }

    @Test
    @DisplayName("🔴 重置成功后【全部会话】被吊销 —— 攻击者手里那个令牌不能还有效")
    void 吊销全部会话() {
        registerByPhone();
        MemberAccessToken a = tokenStore.issue(memberId, MemberSessionContext.empty());
        MemberAccessToken b = tokenStore.issue(memberId, MemberSessionContext.empty());

        MemberPasswordResetResult result = reset(resetCode(phone), NEW_PASSWORD);

        assertTrue(result.success());
        assertEquals(2, result.revokedSessions(),
                "这个数字要给用户看 —— 点忘记密码最常见的原因就是「我怀疑号被人动过」");
        assertNull(tokenStore.resolve(a.value()));
        assertNull(tokenStore.resolve(b.value()),
                "不吊销的话，用户以为自己把人赶出去了，而对方那个 30 天令牌照样能用");
    }

    @Test
    @DisplayName("验证码错 → 拒绝，密码不变")
    void 码不对() {
        registerByPhone();
        String code = resetCode(phone);

        assertEquals(PasswordResetFailReason.SMS_CODE_MISMATCH,
                reset(code.equals("000000") ? "111111" : "000000", NEW_PASSWORD).reason());
        assertTrue(canLogin(OLD_PASSWORD), "被拒之后密码必须原封不动");
    }

    @Test
    @DisplayName("码用过一次就作废 —— 不然拿到一次码能反复改密码")
    void 码不能复用() {
        registerByPhone();
        String code = resetCode(phone);
        assertTrue(reset(code, NEW_PASSWORD).success());

        assertEquals(PasswordResetFailReason.SMS_CODE_EXPIRED,
                reset(code, "SvThird2026x").reason());
    }

    @Test
    @DisplayName("新密码太弱 → 拒绝，而且是在【码验过之后】才说")
    void 密码太弱() {
        registerByPhone();

        assertEquals(PasswordResetFailReason.WEAK_PASSWORD,
                reset(resetCode(phone), "123").reason(),
                "反过来的话，一个不知道码的人可以拿这个接口反复试探密码规则");
    }

    @Test
    @DisplayName("号码格式不对 → BAD_PHONE_FORMAT，不是邮箱那条")
    void 格式不对() {
        registerByPhone();
        phone = "not-a-phone";

        assertEquals(PasswordResetFailReason.BAD_PHONE_FORMAT,
                reset("123456", NEW_PASSWORD).reason());
    }

    @Test
    @DisplayName("🔴 没注册过的号码收不到重置码 —— 给陌生号码发是纯浪费，也是骚扰")
    void 陌生号码不发码() {
        String stranger = freshPhone();

        // 接口照常返回成功（否则它就成了「这个号注册过没有」的查询器）
        assertTrue(memberAuthService.sendSmsCode(
                new SmsCodeSendCmd(SmsScene.RESET_PASSWORD, stranger, freshIp(), null)).success());

        /*
         * 但码【照样存了】—— 这是刻意的：不存的话，攻击者能从「验码返回什么」
         * 反推出这个号有没有账号。真正被省掉的是那条短信（和它的钱）。
         * 这里断言的是「存了」，因为「没发出去」在 LOG 通道下观察不到；
         * 投递决定本身由 MemberSmsCodeIssuerTest 逐格钉住。
         */
        String key = redisService.generateRedisKey("mbr:code:",
                "sms:RESET_PASSWORD:" + piiHasher.hash(MemberPhoneUtil.normalize(stranger)));
        assertNotNull(redisService.get(key),
                "码不存的话，验码那一侧的行为会和正常情况不一样，等于把账号是否存在漏出去");
        redisService.delete(key);
    }
}
