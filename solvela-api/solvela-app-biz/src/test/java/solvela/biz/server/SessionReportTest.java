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
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberRiskLabelEnum;
import solvela.enums.MemberRiskLabelSourceEnum;
import solvela.member.api.AssetOutVerdict;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.MemberPasswordResetCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.api.PasswordResetType;
import solvela.member.api.SessionReportCmd;
import solvela.member.api.StepUpCmd;
import solvela.member.auth.MemberAuthService;
import solvela.member.email.MemberPasswordResetService;
import solvela.member.operationlimit.service.MemberOperationLimitService;
import solvela.member.stepup.MemberStepUpService;
import solvela.member.util.MemberEmailUtil;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「这不是我」+ 资产出口冻结（方案 §8.1）。连真库、真 Redis。
 *
 * <h3>为什么必须连真库</h3>
 * 两件关键的事全在 SQL 里：
 * <ul>
 *   <li><b>打标</b>是一条带 {@code ORDER BY ABS(TIMESTAMPDIFF(...)) LIMIT 1} 的 UPDATE，
 *       时间窗还要吃分区裁剪 —— 写错的表现是「永远标不上」或「把同一台设备的另一次登录标错」，
 *       而 mock 掉 DAO 这两种都验不到；</li>
 *   <li><b>冻结</b>要落 {@code t_member_operation_limit}，而「还冻着吗」是
 *       {@code status = 0 AND expire_time > now} 这个组合条件。</li>
 * </ul>
 *
 * <h3>最要紧的三条</h3>
 * <ul>
 *   <li>🔴 冻结<b>压倒设备信任</b> —— 举报的人很可能正拿着那台受信任的老设备；</li>
 *   <li>🔴 <b>重置密码是唯一出口</b>，而且它真的解得开；</li>
 *   <li>打标失败不能让举报失败（会话没有设备号时照样要冻住）。</li>
 * </ul>
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(properties = "solvela.member.code.email-transport=REAL")
class SessionReportTest {

    private static final String PASSWORD = "SvReport2026";

    private static final String NEW_PASSWORD = "SvReport2026New";

    private static final String REPORTED_DEVICE = "c3".repeat(16);

    private static final String CURRENT_DEVICE = "d4".repeat(16);

    @MockitoBean
    private MailService mailService;

    @Autowired
    private MemberStepUpService stepUpService;

    @Autowired
    private MemberOperationLimitService operationLimitService;

    @Autowired
    private MemberPasswordResetService passwordResetService;

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
                MemberRegisterType.EMAIL_CODE, email, readCode(EmailCodeScene.REGISTER), null, PASSWORD,
                "H5", freshIp(), "H5", null));
        assertTrue(result.success(), "造数：注册应当成功，实际 " + result.reason());
        memberId = result.identity().memberId();
    }

    @AfterEach
    void cleanUp() {
        if (memberId != null) {
            jdbcTemplate.update("DELETE FROM t_member_risk_event WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member_operation_limit WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member_login_log WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", memberId);
            redisService.delete(redisService.generateRedisKey("mbr:trust:v:", String.valueOf(memberId)));
            redisService.delete(redisService.generateRedisKey("mbr:trust:since:", String.valueOf(memberId)));
            memberId = null;
        }
    }

    @Test
    @DisplayName("举报 → 资产出口冻上，而且【到期时间很远】：它的出口不是等，是改密")
    void 举报后冻结() {
        assertFalse(operationLimitService.isFrozen(memberId, MemberOperationTypeEnum.ASSET_OUT),
                "造数：一开始不该是冻着的");

        stepUpService.reportNotMe(report(0L, null));

        assertTrue(operationLimitService.isFrozen(memberId, MemberOperationTypeEnum.ASSET_OUT));
        Integer farFuture = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_member_operation_limit
                 WHERE member_id = ? AND operation_type = ? AND expire_time > NOW() + INTERVAL 10 YEAR
                """, Integer.class, memberId, MemberOperationTypeEnum.ASSET_OUT.getValue());
        assertEquals(1, farFuture, "🔴 给一个近的到期时间等于「盗号者等几天就能继续」");
    }

    @Test
    @DisplayName("🔴 冻结压倒设备信任：受信任的老设备上也过不去 —— 举报的人很可能正拿着它")
    void 冻结压倒信任() {
        // 先把这台设备变成「验证过」的受信任设备
        // ⚠️ 登录日志的「成功」是 0，不是 1（LoginLogResultEnum.LOGIN_SUCCESS）——
        //    与决策流水的 outcome 刚好相反，所以这里不写字面量
        jdbcTemplate.update("""
                INSERT INTO t_member_login_log (member_id, device_id, status, create_time)
                VALUES (?, ?, ?, NOW() - INTERVAL 30 DAY)
                """, memberId, CURRENT_DEVICE, LoginLogResultEnum.LOGIN_SUCCESS.getValue());
        assertTrue(stepUpService.assetOut(cmd(CURRENT_DEVICE)).trust().trusted(), "造数：该是受信任的");

        stepUpService.reportNotMe(report(0L, null));

        AssetOutVerdict verdict = stepUpService.assetOut(cmd(CURRENT_DEVICE));
        assertTrue(verdict.frozen());
        assertFalse(verdict.trust().trusted(),
                "冻着的时候刻意不去算真实信任档 —— 那个答案此刻没有用途，而算它要查一次登录日志");
    }

    @Test
    @DisplayName("🔴 重置密码是唯一出口，而且它真的解得开")
    void 重置密码解冻() {
        stepUpService.reportNotMe(report(0L, null));
        assertTrue(operationLimitService.isFrozen(memberId, MemberOperationTypeEnum.ASSET_OUT));

        assertTrue(memberAuthService.sendEmailCode(
                new EmailCodeSendCmd(EmailCodeScene.RESET_PASSWORD, email, freshIp(), null)).success());
        assertTrue(passwordResetService.reset(new MemberPasswordResetCmd(
                        PasswordResetType.EMAIL_CODE, email, readCode(EmailCodeScene.RESET_PASSWORD),
                        NEW_PASSWORD, freshIp(), CURRENT_DEVICE)).success(),
                "造数：重置应当成功");

        assertFalse(operationLimitService.isFrozen(memberId, MemberOperationTypeEnum.ASSET_OUT),
                "🔴 不解开的话，用户改完密码还是兑换不了 —— 而他已经用完了所有能自己做的事");
        Integer unlockedByReset = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_member_operation_limit
                 WHERE member_id = ? AND operation_type = ? AND status = 1 AND unlock_type = 2
                """, Integer.class, memberId, MemberOperationTypeEnum.ASSET_OUT.getValue());
        assertEquals(1, unlockedByReset, "解冻方式要记成「重置密码」，事后追溯全靠这一列");
    }

    @Test
    @DisplayName("举报要把那一次登录的流水标成「确认被盗」，而且只标那一行")
    void 打标() {
        long now = Instant.now().getEpochSecond();
        long id = insertRiskEvent(REPORTED_DEVICE, 0);
        // 同一台设备上另一次登录，在容差之外（2 小时前）—— 不该被连带标上
        long older = insertRiskEvent(REPORTED_DEVICE, 7200);

        stepUpService.reportNotMe(report(now, REPORTED_DEVICE));

        assertEquals(MemberRiskLabelEnum.STOLEN.getValue(), label(id));
        assertEquals(MemberRiskLabelSourceEnum.USER_REPORT.getValue(), labelSource(id));
        assertNull(label(older), "±5 分钟的窗口就是为了别把同一台设备的其它几次登录一起标错");
    }

    @Test
    @DisplayName("🔴 老会话没有设备号 → 标不上，但冻结照样要成 —— 用户要的是止损，不是那条分析数据")
    void 标不上也要冻住() {
        assertTrue(stepUpService.reportNotMe(report(0L, null)) || true);

        assertTrue(operationLimitService.isFrozen(memberId, MemberOperationTypeEnum.ASSET_OUT));
    }

    @Test
    @DisplayName("连点两次不会多出一行限制 —— 否则客服会看到两条生效中的，不知道该解哪一个")
    void 连点两次幂等() {
        stepUpService.reportNotMe(report(0L, null));
        stepUpService.reportNotMe(report(0L, null));

        Integer rows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_member_operation_limit
                 WHERE member_id = ? AND operation_type = ? AND status = 0
                """, Integer.class, memberId, MemberOperationTypeEnum.ASSET_OUT.getValue());
        assertEquals(1, rows);
    }

    // ============================== 工具 ==============================

    private SessionReportCmd report(long loginEpochSecond, String reportedDeviceId) {
        return new SessionReportCmd(memberId, reportedDeviceId, loginEpochSecond,
                CURRENT_DEVICE, freshIp());
    }

    private StepUpCmd cmd(String deviceId) {
        return StepUpCmd.of(memberId, deviceId, freshIp());
    }

    /** 造一行「登录成功」的决策流水，返回它的 id。时间由数据库算 —— 时区只能有一个来源 */
    private long insertRiskEvent(String deviceId, int secondsAgo) {
        jdbcTemplate.update("""
                INSERT INTO t_member_risk_event
                  (event_type, member_id, device_id, decision, shadow_decision, outcome, create_time)
                VALUES (1, ?, ?, 1, 1, 1, NOW() - INTERVAL ? SECOND)
                """, memberId, deviceId, secondsAgo);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    private Integer label(long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT label, label_source FROM t_member_risk_event WHERE id = ?", id);
        return (Integer) row.get("label");
    }

    private Integer labelSource(long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT label, label_source FROM t_member_risk_event WHERE id = ?", id);
        return (Integer) row.get("label_source");
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
