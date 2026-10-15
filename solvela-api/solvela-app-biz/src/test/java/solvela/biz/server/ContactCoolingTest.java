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
import solvela.enums.LoginLogResultEnum;
import solvela.enums.MemberVerifyChannelEnum;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberEmailBindCmd;
import solvela.member.api.MemberPasswordResetCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.api.PasswordResetFailReason;
import solvela.member.api.PasswordResetType;
import solvela.member.api.StepUpCmd;
import solvela.member.api.StepUpFailReason;
import solvela.member.auth.MemberAuthService;
import solvela.member.contact.ContactCoolingStore;
import solvela.member.email.MemberEmailBindService;
import solvela.member.email.MemberPasswordResetService;
import solvela.member.stepup.MemberStepUpService;
import solvela.member.util.MemberEmailUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新绑联系方式的 7 天冷静期（方案 §6.4）。连真库、真 Redis。
 *
 * <h3>它堵的是一条完整的账号接管链</h3>
 * 首次绑定邮箱<b>不要求任何额外验证</b>，于是一个知道密码的人在新设备上可以
 * 「绑自己的邮箱 → 用它通过二次验证 → 把东西搬走」，或者更糟 ——
 * 「用它走找回密码 → 永久接管」。而我们偏偏要给「没绑就去绑定」一个显眼的按钮，
 * 所以按钮和冷静期<b>必须一起上</b>。
 *
 * <h3>三处都要挡，而且判据要是「绑定时那台设备」</h3>
 * 漏一处这条链就还通着 —— 尤其是找回密码，它是三者里后果最重的那一个。
 * 而主人在自己的老手机上补绑邮箱是最正常的事，不该被拦。
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@ExtendWith(RelaxedRiskRules.class)
@SpringBootTest
@TestPropertySource(properties = {
        "solvela.member.code.email-transport=REAL",
        "solvela.member.risk.cache-refresh=0s",
})
class ContactCoolingTest {

    private static final String PASSWORD = "SvCooling2026";

    /** 主人的老设备：会给它造一条 30 天前的成功登录 → 受信任 */
    private static final String OLD_DEVICE = "a7".repeat(16);

    /** 攻击者那台：没有任何登录记录 → 不受信任 */
    private static final String NEW_DEVICE = "b8".repeat(16);

    @MockitoBean
    private MailService mailService;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private MemberEmailBindService emailBindService;

    @Autowired
    private MemberStepUpService stepUpService;

    @Autowired
    private MemberPasswordResetService passwordResetService;

    @Autowired
    private ContactCoolingStore coolingStore;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;

    /** 手机号注册出来的会员：没有邮箱，正是这条链的起点 */
    private String phone;

    @BeforeEach
    void registerByPhone() {
        phone = freshPhone();
        String ip = freshIp();
        MemberRegisterResult result = memberAuthService.register(MemberRegisterCmd.byPhonePassword(
                phone, PASSWORD,
                TestSmsCode.issue(memberAuthService, redisService, piiHasher, phone, ip),
                "APP", ip, "APP", null));
        assertTrue(result.success(), "造数：注册应当成功，实际 " + result.reason());
        memberId = result.identity().memberId();
        // 主人的老设备
        jdbcTemplate.update("""
                INSERT INTO t_member_login_log (member_id, device_id, status, create_time)
                VALUES (?, ?, ?, NOW() - INTERVAL 30 DAY)
                """, memberId, OLD_DEVICE, LoginLogResultEnum.LOGIN_SUCCESS.getValue());
    }

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_notification WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member_risk_event WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            redisService.delete(
                    redisService.generateRedisKey("mbr:contact:cool:", memberId + ":EMAIL"),
                    redisService.generateRedisKey("mbr:contact:cool:", memberId + ":SMS"),
                    redisService.generateRedisKey("mbr:trust:v:", String.valueOf(memberId)),
                    redisService.generateRedisKey("mbr:trust:since:", String.valueOf(memberId)));
            memberId = null;
        }
    }

    @Test
    @DisplayName("🔴 在不受信任的设备上绑邮箱 → 进冷静期")
    void 新设备上绑进冷静期() {
        bindEmail(freshEmail(), NEW_DEVICE);

        assertTrue(coolingStore.cooling(memberId, MemberVerifyChannelEnum.EMAIL));
    }

    @Test
    @DisplayName("在【受信任】的老设备上绑 → 不进冷静期。主人在自己老手机上补绑邮箱不该被拦")
    void 老设备上绑不冷静() {
        bindEmail(freshEmail(), OLD_DEVICE);

        assertFalse(coolingStore.cooling(memberId, MemberVerifyChannelEnum.EMAIL));
    }

    @Test
    @DisplayName("先在新设备上绑（冷静中）→ 回老设备上重绑一次 → 冷静期解除")
    void 老设备重绑解除冷静() {
        bindEmail(freshEmail(), NEW_DEVICE);
        assertTrue(coolingStore.cooling(memberId, MemberVerifyChannelEnum.EMAIL));

        bindEmail(freshEmail(), OLD_DEVICE);

        assertFalse(coolingStore.cooling(memberId, MemberVerifyChannelEnum.EMAIL));
    }

    @Test
    @DisplayName("🔴 拦截一：冷静期内，二次验证发不出码也验不过 —— 这是「绑自己邮箱搬东西」那条链")
    void 拦住二次验证() {
        bindEmail(freshEmail(), NEW_DEVICE);

        assertEquals(StepUpFailReason.CONTACT_COOLING,
                stepUpService.sendCode(StepUpCmd.of(memberId, NEW_DEVICE, freshIp())).reason());
        assertEquals(StepUpFailReason.CONTACT_COOLING,
                stepUpService.verify(new StepUpCmd(memberId, NEW_DEVICE, freshIp(), "123456")).reason(),
                "🔴 只挡发码不够：码有 5 分钟有效期，冷静期开始之前发出去的照样能拿来验过");
    }

    @Test
    @DisplayName("🔴 拦截二：冷静期内不能用它找回密码 —— 这一处漏了是【永久接管】")
    void 拦住找回密码() {
        String email = freshEmail();
        bindEmail(email, NEW_DEVICE);

        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.RESET_PASSWORD, email, freshIp(), null)).success());
        assertEquals(PasswordResetFailReason.CONTACT_COOLING,
                passwordResetService.reset(new MemberPasswordResetCmd(
                        PasswordResetType.EMAIL_CODE, email,
                        readCode(EmailCodeScene.RESET_PASSWORD, email),
                        "SvBrandNew2026", freshIp(), NEW_DEVICE)).reason());
    }

    @Test
    @DisplayName("冷静期结束后恢复正常（直接把标记清掉，等价于到期）")
    void 到期后恢复() {
        String email = freshEmail();
        bindEmail(email, NEW_DEVICE);
        coolingStore.clear(memberId, MemberVerifyChannelEnum.EMAIL);

        assertTrue(stepUpService.sendCode(StepUpCmd.of(memberId, NEW_DEVICE, freshIp())).success());
    }

    @Test
    @DisplayName("绑定成功要给主人推一条站内信 —— 他得先【知道】有人动了账号，才会去改密码")
    void 绑定要通知() {
        bindEmail(freshEmail(), NEW_DEVICE);

        Integer rows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_member_notification
                 WHERE member_id = ? AND template_code = 'CONTACT_BOUND'
                """, Integer.class, memberId);
        assertEquals(1, rows);
    }

    // ============================== 工具 ==============================

    private void bindEmail(String email, String deviceId) {
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.BIND, email, freshIp(), memberId)).success());
        assertTrue(emailBindService.bind(new MemberEmailBindCmd(memberId, email,
                        readCode(EmailCodeScene.BIND, email), PASSWORD, null, freshIp(), deviceId))
                        .success(),
                "造数：绑定应当成功");
    }

    private String readCode(EmailCodeScene scene, String email) {
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    private static String freshEmail() {
        return "sv" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
    }

    private static String freshPhone() {
        return "13" + (100_000_000L + ThreadLocalRandom.current().nextLong(899_999_999L));
    }

    /** 198.18.0.0/15：整段保留（RFC 2544），不会被当成真实归属地。理由见 PhoneBindTest */
    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198.18." + r.nextInt(256) + "." + (1 + r.nextInt(250));
    }
}
