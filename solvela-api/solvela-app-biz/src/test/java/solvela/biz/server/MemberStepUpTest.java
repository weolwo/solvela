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
import solvela.enums.LoginLogResultEnum;
import solvela.member.api.DeviceTrust;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.api.StepUpCmd;
import solvela.member.api.StepUpFailReason;
import solvela.member.api.StepUpResult;
import solvela.member.auth.MemberAuthService;
import solvela.member.stepup.DeviceTrustStore;
import solvela.member.stepup.MemberStepUpService;
import solvela.member.util.MemberEmailUtil;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 二次验证（设备信任）的端到端验收：连真实库、真实 Redis，只把发信 mock 掉。
 *
 * <h3>为什么必须连真库</h3>
 * 「老交情」那一档是一条手写 SQL（{@code MemberLoginLogDao.existsTrustedLogin}），
 * 时间比较全在数据库里做（{@code NOW() - INTERVAL}、{@code FROM_UNIXTIME}）。
 * 这类 SQL 写错的表现是「门槛静默偏几个小时」或「永远不命中」，mock 掉 DAO 就什么都验不到。
 * 登录记录的时间也在库里算（{@code NOW() - INTERVAL 8 DAY}），不从 Java 传 —— 理由同上。
 *
 * <h3>最要紧的三条</h3>
 * <ul>
 *   <li>失败的登录不算「老交情」；</li>
 *   <li>🔴 在一台新设备上点「下线其他设备」<b>不能</b>把自己洗成唯一受信任的设备；</li>
 *   <li>🔴 公开发码接口（邮箱由客户端填）不能被拿来发二次验证信。</li>
 * </ul>
 *
 * @Date 2026-09-26
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
// 理由同 EmailRegisterAndLoginTest：LOG 通道下 MailService 本来就不会被调，「不该发信」的断言会永真
@TestPropertySource(properties = "solvela.member.code.email-transport=REAL")
class MemberStepUpTest {

    /** 两台设备号：32 位小写 hex，与 DeviceTokenCodec 签发的形状一致 */
    private static final String OLD_DEVICE = "a1".repeat(16);

    private static final String NEW_DEVICE = "b2".repeat(16);

    @MockitoBean
    private MailService mailService;

    @Autowired
    private MemberStepUpService stepUpService;

    @Autowired
    private DeviceTrustStore trustStore;

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
                MemberRegisterType.EMAIL_CODE, email, readCode(EmailCodeScene.REGISTER), null, null,
                "H5", freshIp(), "H5", null));
        assertTrue(result.success(), "造数：注册应当成功，实际 " + result.reason());
        memberId = result.identity().memberId();
        // 注册那封信是异步发的：等它落地再清掉调用记录，否则下面「不该发信」的断言会把它算进去
        verify(mailService, timeout(3000)).sendMail(eq(MailTemplateCodeEnum.MEMBER_REGISTER_CODE), any(), any());
        clearInvocations(mailService);
    }

    /** 本类造的会员、登录记录、信任键全部清掉 —— 联调库不该每跑一次测试就长一批会员 */
    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            redisService.delete(redisService.generateRedisKey("mbr:trust:v:", String.valueOf(memberId)));
            redisService.delete(redisService.generateRedisKey("mbr:trust:since:", String.valueOf(memberId)));
            memberId = null;
        }
    }

    // ============================== 信任判断 ==============================

    @Test
    @DisplayName("没有设备号 → NO_DEVICE，发码也直接拒绝（验了也没处记住）")
    void 无设备() {
        assertEquals(DeviceTrust.NO_DEVICE, stepUpService.check(cmd(null)));
        assertEquals(StepUpFailReason.NO_DEVICE, stepUpService.sendCode(cmd(null)).reason());
        verify(mailService, never()).sendMail(eq(MailTemplateCodeEnum.MEMBER_STEP_UP_CODE), any(), any());
    }

    @Test
    @DisplayName("没有任何登录记录的设备 → NEW_DEVICE")
    void 新设备() {
        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(NEW_DEVICE)));
    }

    @Test
    @DisplayName("首次成功登录不满门槛（1 天前）→ 仍是 NEW_DEVICE；满门槛（8 天前）→ HISTORY")
    void 老交情按门槛算() {
        insertLogin(OLD_DEVICE, 1, LoginLogResultEnum.LOGIN_SUCCESS);
        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(OLD_DEVICE)));

        insertLogin(OLD_DEVICE, 8, LoginLogResultEnum.LOGIN_SUCCESS);
        assertEquals(DeviceTrust.HISTORY, stepUpService.check(cmd(OLD_DEVICE)));
    }

    @Test
    @DisplayName("🔴 失败的登录不算老交情 —— 否则撞库时输错的那几次会给攻击者的设备攒信任")
    void 失败登录不算() {
        insertLogin(OLD_DEVICE, 30, LoginLogResultEnum.LOGIN_FAIL);
        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(OLD_DEVICE)));
    }

    @Test
    @DisplayName("别的会员在这台设备上的老记录，不算我的老交情")
    void 只认本人的登录记录() {
        jdbcTemplate.update("""
                INSERT INTO t_member_login_log (member_id, device_id, status, create_time)
                VALUES (?, ?, ?, NOW() - INTERVAL 30 DAY)
                """, memberId + 1_000_000_000L, OLD_DEVICE, LoginLogResultEnum.LOGIN_SUCCESS.getValue());
        try {
            assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(OLD_DEVICE)));
        } finally {
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId + 1_000_000_000L);
        }
    }

    // ============================== 发码 / 验码 ==============================

    @Test
    @DisplayName("发码寄到【已绑定】的邮箱，用二次验证模板；验对之后这台设备变成 VERIFIED")
    void 发码验码() {
        StepUpResult sent = stepUpService.sendCode(cmd(NEW_DEVICE));
        assertTrue(sent.success(), "实际 " + sent.reason());
        assertEquals(MemberEmailUtil.mask(MemberEmailUtil.normalize(email)), sent.maskedEmail());
        verify(mailService, timeout(3000)).sendMail(
                eq(MailTemplateCodeEnum.MEMBER_STEP_UP_CODE), any(), eq(List.of(MemberEmailUtil.normalize(email))));

        String code = readCode(EmailCodeScene.STEP_UP);
        assertNotNull(code, "发码之后 Redis 里应当有一份");
        String wrong = code.equals("000000") ? "111111" : "000000";
        assertEquals(StepUpFailReason.CODE_MISMATCH, stepUpService.verify(codeCmd(NEW_DEVICE, wrong)).reason());
        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(NEW_DEVICE)), "验错不该留下任何信任");

        assertTrue(stepUpService.verify(codeCmd(NEW_DEVICE, code)).success());
        assertEquals(DeviceTrust.VERIFIED, stepUpService.check(cmd(NEW_DEVICE)));

        assertEquals(StepUpFailReason.CODE_EXPIRED, stepUpService.verify(codeCmd(NEW_DEVICE, code)).reason(),
                "同一个码不能用第二次");
    }

    @Test
    @DisplayName("🔴 公开发码接口传 STEP_UP 场景：照常返回（不泄露信息），但【不寄信】")
    void 公开接口发不出二次验证信() {
        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.STEP_UP, "sv" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@example.com", freshIp(), null)).success());
        verify(mailService, never()).sendMail(eq(MailTemplateCodeEnum.MEMBER_STEP_UP_CODE), any(), any());
    }

    // ============================== 撤销 ==============================

    @Test
    @DisplayName("全部撤销（重置密码 / 冻结）后：验证过的清空，此前的登录历史也不再算数")
    void 全部撤销() {
        insertLogin(OLD_DEVICE, 8, LoginLogResultEnum.LOGIN_SUCCESS);
        trustStore.markVerified(memberId, NEW_DEVICE);
        assertEquals(DeviceTrust.HISTORY, stepUpService.check(cmd(OLD_DEVICE)));
        assertEquals(DeviceTrust.VERIFIED, stepUpService.check(cmd(NEW_DEVICE)));

        stepUpService.revokeAll(memberId);

        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(OLD_DEVICE)),
                "撤销之前的登录历史还在算数的话，攻击者那台登录满 7 天的设备改完密码照样受信任");
        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(NEW_DEVICE)));
    }

    @Test
    @DisplayName("从受信任的设备点「下线其他设备」：自己保留信任，其余设备失去信任")
    void 受信任设备下线其他() {
        insertLogin(OLD_DEVICE, 8, LoginLogResultEnum.LOGIN_SUCCESS);
        trustStore.markVerified(memberId, NEW_DEVICE);

        assertTrue(stepUpService.revokeOthers(cmd(OLD_DEVICE)));

        assertTrue(stepUpService.check(cmd(OLD_DEVICE)).trusted());
        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(NEW_DEVICE)));
    }

    @Test
    @DisplayName("🔴 从一台新设备点「下线其他设备」：不能把自己洗成受信任，也要把主人的设备挤掉信任")
    void 新设备下线其他不能洗白() {
        insertLogin(OLD_DEVICE, 8, LoginLogResultEnum.LOGIN_SUCCESS);

        assertFalse(stepUpService.revokeOthers(cmd(NEW_DEVICE)));

        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(NEW_DEVICE)),
                "攻击者在新设备上点一下这个按钮，就成了唯一受信任的设备 —— 正是要堵的那条路");
        assertEquals(DeviceTrust.NEW_DEVICE, stepUpService.check(cmd(OLD_DEVICE)),
                "撤销对所有设备生效，包括主人的老设备：主人要再验一次，这是可接受的代价");
    }

    // ============================== 工具 ==============================

    private StepUpCmd cmd(String deviceId) {
        return StepUpCmd.of(memberId, deviceId, freshIp());
    }

    private StepUpCmd codeCmd(String deviceId, String code) {
        return new StepUpCmd(memberId, deviceId, freshIp(), code);
    }

    /** 时间在库里算，不从 Java 传 —— 与被测 SQL 同一个会话时区 */
    private void insertLogin(String deviceId, int daysAgo, LoginLogResultEnum status) {
        jdbcTemplate.update("""
                INSERT INTO t_member_login_log (member_id, device_id, status, create_time)
                VALUES (?, ?, ?, NOW() - INTERVAL ? DAY)
                """, memberId, deviceId, status.getValue(), daysAgo);
    }

    private String readCode(EmailCodeScene scene) {
        String key = redisService.generateRedisKey("mbr:code:",
                "email:" + scene.name() + ":" + piiHasher.hash(MemberEmailUtil.normalize(email)));
        String stored = redisService.get(key);
        return stored == null ? null : stored.split("\\|")[0];
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + (1 + r.nextInt(250)) + "." + (1 + r.nextInt(250));
    }
}
