package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskOutcomeEnum;
import solvela.member.risk.domain.dto.MemberRiskStatDTO;
import solvela.member.risk.service.MemberRiskAdminService;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 看板的聚合查询。连真库 —— 这几条 SQL 的正确性全在 SQL 里，mock 掉就什么也验不到。
 *
 * <h3>最要紧的两条</h3>
 * <ul>
 *   <li>🔴 <b>核身通过数靠 {@code ref_event_id} 指回签票那一行来数</b>。
 *       单独数「验证通过」的条数会把窗口<b>之前</b>签的票算进来 —— 于是通过率可能大于 100%，
 *       而那个数字一旦出现过一次，整块看板就没人信了；</li>
 *   <li>🔴 <b>矩阵三列要求非空</b>：查无此人时它们是 NULL，混进来会多出一格
 *       「全是空」的，而那一格不对应矩阵里的任何位置。</li>
 * </ul>
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class MemberRiskStatTest {

    @Autowired
    private MemberRiskAdminService riskAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本类造的数据都挂在这个会员号下，清理时一把删掉 */
    private final long memberId = 990_000_000L + ThreadLocalRandom.current().nextInt(9_000_000);

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_member_risk_hit WHERE event_id IN "
                + "(SELECT id FROM t_member_risk_event WHERE member_id = ?)", memberId);
        jdbcTemplate.update("DELETE FROM t_member_risk_event WHERE member_id = ?", memberId);
    }

    @Test
    @DisplayName("结论分布、矩阵各格、规则命中、结论不一致 —— 四组数都从同一个窗口里取")
    void 四组数() {
        long pass = insertEvent(MemberRiskDecisionEnum.PASS, MemberRiskDecisionEnum.PASS,
                MemberRiskOutcomeEnum.SUCCESS, 2, 2, 0);
        insertEvent(MemberRiskDecisionEnum.CAPTCHA, MemberRiskDecisionEnum.OTP,
                MemberRiskOutcomeEnum.CAPTCHA_REQUIRED, 1, 0, 0);
        insertHit(pass, "LOGIN_MATRIX", MemberRiskModeEnum.ENFORCE, 0);
        insertHit(pass, "NEW_COUNTRY", MemberRiskModeEnum.SHADOW, 1);

        MemberRiskStatDTO stat = riskAdminService.stat(from(), to());

        assertTrue(stat.getTotalCount() >= 2);
        assertTrue(stat.getDecisionDaily().stream()
                .anyMatch(d -> MemberRiskDecisionEnum.PASS.getValue().equals(d.getDecision())));
        assertTrue(stat.getMatrixCells().stream()
                        .anyMatch(c -> c.getDeviceTrust() == 2 && c.getIpFamiliar() == 2 && c.getDormant() == 0),
                "矩阵那一格要数得出来");
        assertTrue(stat.getShadowDiffCount() >= 1, "🔴 这个数就是「切生效会多打扰多少人」");
        assertTrue(stat.getRuleHits().stream()
                        .anyMatch(r -> "NEW_COUNTRY".equals(r.getRuleCode()) && r.getErrorCount() >= 1),
                "取特征失败要单独数出来 —— 那条规则写着「生效」却其实没在工作");
    }

    @Test
    @DisplayName("🔴 查无此人那几行不进矩阵统计：它们的三列是 NULL，混进来会多出一格不存在的位置")
    void 查无此人不进矩阵() {
        insertEventWithNulls();

        MemberRiskStatDTO stat = riskAdminService.stat(from(), to());

        assertTrue(stat.getMatrixCells().stream()
                .allMatch(c -> c.getDeviceTrust() != null && c.getIpFamiliar() != null
                        && c.getDormant() != null));
    }

    @Test
    @DisplayName("🔴 核身通过只数「签票也在窗口内」的那些，否则通过率能大于 100%")
    void 核身漏斗() {
        /*
         * ⚠️ 看板是【全局】聚合，不按会员过滤 —— 这个窗口里本来就可能有别的用例造的数据。
         *    所以这里比的是【增量】，不是绝对值。写成绝对值的用例会在别人加一个用例时莫名挂掉。
         */
        MemberRiskStatDTO.OtpFunnel before = riskAdminService.stat(from(), to()).getOtpFunnel();

        long challenged = insertEvent(MemberRiskDecisionEnum.OTP, MemberRiskDecisionEnum.OTP,
                MemberRiskOutcomeEnum.CHALLENGED, 1, 0, 0);
        insertVerifyEvent(challenged);
        // 一张「窗口之前签的票」的验码：ref 指向一个不在窗口里的 id
        insertVerifyEvent(1L);

        MemberRiskStatDTO.OtpFunnel after = riskAdminService.stat(from(), to()).getOtpFunnel();

        assertEquals(1, after.challenged() - before.challenged());
        assertEquals(1, after.verified() - before.verified(),
                "两条都数进去的话通过率就是 200% —— 那个数字出现过一次，整块看板就没人信了");
    }

    @Test
    @DisplayName("窗口太长直接拒绝，不悄悄截断 —— 截断的话页面上的筛选器会给出一个没人知道的数")
    void 窗口上限() {
        assertThrows(IllegalArgumentException.class,
                () -> riskAdminService.stat(LocalDateTime.now().minusDays(200), LocalDateTime.now()));
    }

    @Test
    @DisplayName("开始晚于结束直接拒绝")
    void 时间顺序() {
        assertThrows(IllegalArgumentException.class,
                () -> riskAdminService.stat(LocalDateTime.now(), LocalDateTime.now().minusDays(1)));
    }

    @Test
    @DisplayName("不传时间兜最近 30 天（跑得通、不炸）")
    void 默认窗口() {
        assertTrue(riskAdminService.stat(null, null).getTotalCount() >= 0);
    }

    // ============================== 造数 ==============================

    private static LocalDateTime from() {
        return LocalDateTime.now().minusHours(1);
    }

    private static LocalDateTime to() {
        return LocalDateTime.now().plusHours(1);
    }

    private long insertEvent(MemberRiskDecisionEnum decision, MemberRiskDecisionEnum shadow,
                             MemberRiskOutcomeEnum outcome, int deviceTrust, int ipFamiliar, int dormant) {
        jdbcTemplate.update("""
                INSERT INTO t_member_risk_event
                  (event_type, member_id, device_trust, ip_familiar, dormant,
                   decision, shadow_decision, outcome, create_time)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, NOW())
                """, memberId, deviceTrust, ipFamiliar, dormant,
                decision.getValue(), shadow.getValue(), outcome.getValue());
        return lastId();
    }

    /** 查无此人的那一行：三列都是 NULL */
    private void insertEventWithNulls() {
        jdbcTemplate.update("""
                INSERT INTO t_member_risk_event
                  (event_type, member_id, decision, shadow_decision, outcome, create_time)
                VALUES (1, ?, 1, 1, 3, NOW())
                """, memberId);
    }

    private void insertVerifyEvent(long refEventId) {
        jdbcTemplate.update("""
                INSERT INTO t_member_risk_event
                  (event_type, ref_event_id, member_id, decision, shadow_decision, outcome, create_time)
                VALUES (2, ?, ?, 1, 1, ?, NOW())
                """, refEventId, memberId, MemberRiskOutcomeEnum.VERIFY_PASSED.getValue());
    }

    private void insertHit(long eventId, String ruleCode, MemberRiskModeEnum mode, int error) {
        jdbcTemplate.update("""
                INSERT INTO t_member_risk_hit (event_id, rule_code, mode, score, error, create_time)
                VALUES (?, ?, ?, 0, ?, NOW())
                """, eventId, ruleCode, mode.getValue(), error);
    }

    private long lastId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }
}
