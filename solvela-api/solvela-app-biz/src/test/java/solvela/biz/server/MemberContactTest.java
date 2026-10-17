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
import solvela.member.api.MemberContactView;
import solvela.member.api.MemberEmailBindCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.auth.MemberAuthService;
import solvela.member.util.MemberEmailUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「我的联系方式」：C 端账号安全页要显示的那两行。
 *
 * <h3>🔴 这个接口唯一的风险就是<b>把明文漏出去</b></h3>
 * 整套 {@code PiiCipher} / {@code PiiHasher} 的意义，是让手机号和邮箱
 * 不以明文形式离开数据库。这个接口是唯一一处<b>主动解密再下发</b>的地方 ——
 * 解完忘了打码，前面所有加密一次作废，而且返回 200、日志干净、没有任何迹象。
 *
 * <p>所以本类第一条用例断言的不是「显示得对不对」，而是
 * <b>返回值里不含明文</b>。
 *
 * <h3>passwordSet 为什么也在这里</h3>
 * 换绑邮箱的界面靠它决定给什么选项：没设过密码的会员只能走「旧邮箱验证码」，
 * 给他一个「输入当前密码」的框，是让他对着一个填不了的东西发愁。
 *
 * @Date 2026-09-10
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class MemberContactTest {

    private static final String PASSWORD = "SvContact2026";

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
        return "contact" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
    }

    private static String freshPhone() {
        return "13" + (100_000_000 + ThreadLocalRandom.current().nextInt(800_000_000));
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }

    private String sendAndReadCode(EmailCodeScene scene, String email) {
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(scene, email, freshIp(), memberId)).success());
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

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

    @Test
    @DisplayName("🔴 返回值里【一个明文字符都没有】—— 这是整套 PII 加密的最后一道口子")
    void 不下发明文() {
        String phone = registerByPhone();
        String email = freshEmail();
        assertTrue(memberAuthService.bindEmail(new MemberEmailBindCmd(
                memberId, email, sendAndReadCode(EmailCodeScene.BIND, email),
                null, null, freshIp(), null)).success());

        MemberContactView view = memberAuthService.getContact(memberId);

        assertNotNull(view.phone());
        assertNotNull(view.email());
        assertFalse(view.phone().contains(phone),
                "手机号明文漏出去了。解完密忘了打码，前面所有加密一次作废，"
                        + "而且返回 200、日志干净、没有任何迹象。实际：" + view.phone());
        assertFalse(view.email().contains(email),
                "邮箱明文漏出去了。实际：" + view.email());
        assertTrue(view.phone().contains("*"), "得看得出是打过码的，实际：" + view.phone());
        assertTrue(view.email().contains("*"), "得看得出是打过码的，实际：" + view.email());
    }

    @Test
    @DisplayName("打码之后仍然认得出是哪个 —— 全打成星号等于没显示")
    void 打码保留可辨识的部分() {
        String phone = registerByPhone();

        MemberContactView view = memberAuthService.getContact(memberId);

        assertTrue(view.phone().startsWith(phone.substring(0, 3)),
                "前三位要留着：用户就是靠它认出「这是我哪个号」。实际：" + view.phone());
    }

    @Test
    @DisplayName("没绑邮箱 → email 为 null，而不是空串")
    void 没绑邮箱() {
        registerByPhone();

        MemberContactView view = memberAuthService.getContact(memberId);

        assertNull(view.email(), "空串会让前端的「未绑定」判断写成两种，迟早漏一种");
        assertTrue(view.passwordSet(), "手机号注册必然设过密码");
    }

    @Test
    @DisplayName("🔴 邮箱注册不设密码 → passwordSet=false，换绑界面据此不给「输入当前密码」")
    void 无密码会员() {
        String email = freshEmail();
        String code = sendAndReadCode(EmailCodeScene.REGISTER, email);
        MemberRegisterResult r = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, code, null, null,
                "H5", freshIp(), "H5", null));
        assertTrue(r.success(), "前提不成立：" + r.reason());
        memberId = r.identity().memberId();

        MemberContactView view = memberAuthService.getContact(memberId);

        assertFalse(view.passwordSet(),
                "报成 true 的话，界面会给他一个「输入当前密码」的框 —— 而他从来没设过密码");
        assertNull(view.phone(), "邮箱注册的会员没有手机号");
        assertNotNull(view.email());
    }

    @Test
    @DisplayName("会员不存在 → 三个字段都是空，不抛异常")
    void 会员不存在() {
        MemberContactView view = memberAuthService.getContact(-1L);

        assertNull(view.phone());
        assertNull(view.email());
        assertFalse(view.passwordSet());
    }
}
