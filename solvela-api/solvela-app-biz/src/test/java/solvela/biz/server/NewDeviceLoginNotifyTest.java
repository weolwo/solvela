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
import solvela.member.api.DeviceRegisterCmd;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.auth.MemberAuthService;
import solvela.member.device.DeviceService;
import solvela.member.util.MemberEmailUtil;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 新设备登录提醒：真库、真 Redis，只把发信 mock 掉。
 *
 * <h3>钉住的三条</h3>
 * <ul>
 *   <li>注册后的第一次登录<b>不提醒</b> —— 否则每个新用户都收到一封「异地登录」告警，只会学会忽略它；</li>
 *   <li>老设备再登录不提醒；</li>
 *   <li>🔴 换一台从没登过的设备 → 邮件 + 站内信都发（站内信归 SYSTEM，关不掉）。</li>
 * </ul>
 *
 * @Date 2026-09-27
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(properties = "solvela.member.code.email-transport=REAL")
class NewDeviceLoginNotifyTest {

    private static final String PASSWORD = "SvNewDevice2026";

    @MockitoBean
    private MailService mailService;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PiiHasher piiHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;

    private String email;

    private String deviceA;

    private String deviceB;

    @BeforeEach
    void setUp() {
        email = "svnd" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com";
        String ip = freshIp();
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.REGISTER, email, ip, null)).success());
        String key = redisService.generateRedisKey("mbr:code:",
                "email:REGISTER:" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        MemberRegisterResult registered = memberAuthService.register(new MemberRegisterCmd(
                MemberRegisterType.EMAIL_CODE, email, redisService.get(key).split("\\|")[0], null, PASSWORD,
                "H5", ip, "H5", null));
        assertTrue(registered.success(), "造数：注册应当成功，实际 " + registered.reason());
        memberId = registered.identity().memberId();

        deviceA = deviceService.register(new DeviceRegisterCmd("H5", null, null, null, freshIp())).deviceId();
        deviceB = deviceService.register(new DeviceRegisterCmd("APP", null, null, null, freshIp())).deviceId();
        // 注册那封验证码信是异步发的：等它落地再清掉调用记录
        verify(mailService, timeout(3000)).sendMail(eq(MailTemplateCodeEnum.MEMBER_REGISTER_CODE), any(), any());
        clearInvocations(mailService);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_member_notification WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM t_device WHERE device_id IN (?, ?)", deviceA, deviceB);
    }

    @Test
    @DisplayName("注册后第一次登录、老设备再登录 → 都不提醒")
    void 首次与老设备不提醒() {
        assertTrue(login(deviceA), "第一次登录");
        assertTrue(login(deviceA), "同一台设备再登");

        verify(mailService, after(1500).never()).sendMail(eq(MailTemplateCodeEnum.MEMBER_NEW_DEVICE_LOGIN), any(), any());
        assertEquals(0, inboxCount(), "注册后第一次登录就发「新设备告警」，新用户只会学会忽略这类信");
    }

    @Test
    @DisplayName("🔴 在一台从没登过的设备上登录 → 邮件发到绑定邮箱 + 一条站内信")
    void 新设备提醒() {
        assertTrue(login(deviceA));

        assertTrue(login(deviceB));

        verify(mailService, timeout(3000)).sendMail(eq(MailTemplateCodeEnum.MEMBER_NEW_DEVICE_LOGIN), any(),
                eq(List.of(MemberEmailUtil.normalize(email))));
        waitForInbox(1);
        assertEquals(1, inboxCount());
    }

    @Test
    @DisplayName("没有设备号的登录不提醒 —— 判断不了新不新，宁可不发也不乱发")
    void 无设备不提醒() {
        assertTrue(login(deviceA));

        assertTrue(login(null));

        verify(mailService, after(1500).never()).sendMail(eq(MailTemplateCodeEnum.MEMBER_NEW_DEVICE_LOGIN), any(), any());
    }

    // ============================== 工具 ==============================

    private boolean login(String deviceId) {
        return memberAuthService.authenticate(new MemberAuthCmd(
                MemberLoginType.EMAIL_PASSWORD, email, PASSWORD, null, "H5", freshIp(), deviceId)).success();
    }

    private int inboxCount() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_member_notification WHERE member_id = ? AND template_code = 'NEW_DEVICE_LOGIN'",
                Integer.class, memberId);
        return n == null ? 0 : n;
    }

    /** 站内信是异步写的 */
    private void waitForInbox(int expected) {
        long deadline = System.currentTimeMillis() + 3000;
        while (inboxCount() < expected && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }
}
