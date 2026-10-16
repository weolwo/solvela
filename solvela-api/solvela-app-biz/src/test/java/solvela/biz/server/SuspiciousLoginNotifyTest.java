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
import solvela.base.mail.MailTemplateCodeEnum;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiHasher;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.auth.MemberAuthService;
import solvela.member.risk.SuspiciousLoginNotifier;
import solvela.member.util.MemberEmailUtil;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * {@code RiskAction.NOTIFY_OWNER}：「我们拦下了一次可疑登录」。
 *
 * <h3>钉住的两条，都是「别把这封信发废」</h3>
 * <ul>
 *   <li>🔴 <b>按会员节流</b>。一次撞库会对同一个账号连试几十次，每一次都可能判到 OTP_STRONG ——
 *       不节流的话主人收到三十封一模一样的信，而他第一反应是把发件人拉黑。
 *       发废了的后果是：真出事的那一封也会被忽略；</li>
 *   <li>🔴 <b>信里一个链接都没有</b>。钓鱼邮件最爱模仿这类「安全提醒」，
 *       我们自己的信里没有链接，用户才有依据判断「带链接的那封是假的」。</li>
 * </ul>
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
// 理由同 EmailRegisterAndLoginTest：LOG 通道下 MailService 本来就不会被调，断言会永真
@TestPropertySource(properties = "solvela.member.code.email-transport=REAL")
class SuspiciousLoginNotifyTest {

    @MockitoBean
    private MailService mailService;

    @Autowired
    private SuspiciousLoginNotifier notifier;

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

    @BeforeEach
    void registerMember() {
        email = "sv" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.REGISTER, email, freshIp(), null)).success());
        MemberRegisterResult result = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, readCode(), null, null,
                "H5", freshIp(), "H5", null));
        assertTrue(result.success(), "造数：注册应当成功，实际 " + result.reason());
        memberId = result.identity().memberId();
        // 注册那封信是异步发的：等它落地再清掉调用记录，否则下面的断言会把它算进来
        verify(mailService, timeout(3000)).sendMail(eq(MailTemplateCodeEnum.MEMBER_REGISTER_CODE), any(), any());
        clearInvocations(mailService);
    }

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_notification WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            redisService.delete(redisService.generateRedisKey(
                    "mbr:risk:notify-owner:", String.valueOf(memberId)));
            memberId = null;
        }
    }

    @Test
    @DisplayName("通知主人：一封邮件 + 一条站内信，站内信归 SYSTEM（关不掉免打扰）")
    void 发一封() {
        notifier.notifyOwner(memberId, "1.2.3.4", "APP");

        verify(mailService, timeout(3000))
                .sendMail(eq(MailTemplateCodeEnum.MEMBER_SUSPICIOUS_LOGIN), any(), any());
        assertEquals(1, notificationCount());
        assertEquals("SYSTEM", jdbcTemplate.queryForObject("""
                SELECT category FROM t_member_notification
                 WHERE member_id = ? AND template_code = 'SUSPICIOUS_LOGIN'
                """, String.class, memberId), "「有人在试你的密码」不该能被免打扰关掉");
    }

    @Test
    @DisplayName("🔴 连着来十次只发一封 —— 三十封一样的信换来的是用户把发件人拉黑")
    void 节流() {
        for (int i = 0; i < 10; i++) {
            notifier.notifyOwner(memberId, "1.2.3.4", "APP");
        }

        verify(mailService, timeout(3000))
                .sendMail(eq(MailTemplateCodeEnum.MEMBER_SUSPICIOUS_LOGIN), any(), any());
        assertEquals(1, notificationCount(), "节流失效的表现是主人收到一串一模一样的信");
    }

    @Test
    @DisplayName("🔴 信里一个链接都没有 —— 否则我们自己教会了用户去点「安全提醒」里的链接")
    void 信里没有链接() {
        String content = jdbcTemplate.queryForObject("""
                SELECT template_content FROM t_mail_template WHERE template_code = 'member_suspicious_login'
                """, String.class);

        assertTrue(content != null && !content.contains("<a "),
                "钓鱼邮件最爱模仿这类信。我们的信里没有链接，用户才有依据判断带链接的那封是假的");
        assertTrue(content.contains("没有任何链接"), "而且要把这件事写给用户看");
    }

    @Test
    @DisplayName("会员号为空时什么都不做 —— 查无此人时引擎不会带这个动作，这里再兜一次")
    void 没有会员号() {
        notifier.notifyOwner(null, "1.2.3.4", "APP");

        verify(mailService, org.mockito.Mockito.never()).sendMail(any(), any(), any());
    }

    // ============================== 工具 ==============================

    private int notificationCount() {
        Integer rows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_member_notification
                 WHERE member_id = ? AND template_code = 'SUSPICIOUS_LOGIN'
                """, Integer.class, memberId);
        return rows == null ? 0 : rows;
    }

    private String readCode() {
        String key = redisService.generateRedisKey("mbr:code:",
                "email:REGISTER:" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    /** 198.18.0.0/15：整段保留，不会被当成真实归属地。理由见 PhoneBindTest */
    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "198.18." + r.nextInt(256) + "." + (1 + r.nextInt(250));
    }
}
