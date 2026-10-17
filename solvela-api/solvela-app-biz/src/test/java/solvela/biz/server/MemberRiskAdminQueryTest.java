package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.domain.PageResult;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskOutcomeEnum;
import solvela.member.MemberRiskEvent;
import solvela.member.MemberRiskHit;
import solvela.member.MemberRiskRule;
import solvela.member.risk.dao.MemberRiskEventDao;
import solvela.member.risk.dao.MemberRiskHitDao;
import solvela.member.risk.domain.dto.MemberRiskEventDTO;
import solvela.member.risk.domain.dto.MemberRiskEventDetailDTO;
import solvela.member.risk.domain.query.MemberRiskEventQuery;
import solvela.member.risk.service.MemberRiskAdminService;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台那两个页面背后的查询，<b>对着真库跑</b>。
 *
 * <h3>为什么必须是集成测试</h3>
 * 这些查询里最容易错的两件事都只有真 MySQL 才看得出来：
 * <ul>
 *   <li>{@code identity_hash} 是 {@code binary(32)}，出要 {@code HEX}、入要 {@code UNHEX} ——
 *       漏了的表现是「按手机号查永远 0 条」，而 SQL 看着完全正常；</li>
 *   <li>两张表都按月分区，关联与详情查询都要带 {@code create_time}。</li>
 * </ul>
 *
 * <p>所有数据都是本用例自己造的，{@code @AfterEach} 删掉。
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@DisplayName("风控后台查询")
class MemberRiskAdminQueryTest {

    /** 真实业务不会出现的会员号 */
    private static final long MEMBER_ID = 9_999_999_996L;

    private static final String HASH = "ab".repeat(32);

    @Autowired
    private MemberRiskAdminService adminService;

    @Autowired
    private MemberRiskEventDao eventDao;

    @Autowired
    private MemberRiskHitDao hitDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_member_risk_hit WHERE event_id IN "
                + "(SELECT id FROM t_member_risk_event WHERE member_id = ?)", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM t_member_risk_event WHERE member_id = ? OR identity_hash = UNHEX(?)",
                MEMBER_ID, HASH);
    }

    // ------------------------------------------------------------------ 流水

    @Test
    @DisplayName("🔴 按手机号（摘要）查得到 —— 这是「查无此人的尝试」唯一的聚合口径")
    void queryByIdentityHash() {
        // 没有 member_id 的那种行：登录日志里一行都不留，只有这张表有
        writeEvent(null, HASH, MemberRiskDecisionEnum.CAPTCHA, MemberRiskOutcomeEnum.UNKNOWN_ACCOUNT);

        MemberRiskEventQuery query = newQuery();
        query.setIdentityHashHex(HASH);
        PageResult<MemberRiskEventDTO> page = adminService.queryEventPage(query);

        assertEquals(1, page.list().size(), "漏了 UNHEX 的话这里永远是 0 条，而 SQL 看着很正常");
        MemberRiskEventDTO row = page.list().get(0);
        assertNull(row.getMemberId());
        assertEquals(HASH.toUpperCase(), row.getIdentityHashHex(), "HEX() 出来是大写");
        assertEquals(MemberRiskOutcomeEnum.UNKNOWN_ACCOUNT, row.getOutcome());
    }

    @Test
    @DisplayName("按会员号、结论、结局筛")
    void queryByFilters() {
        writeEvent(MEMBER_ID, HASH, MemberRiskDecisionEnum.OTP, MemberRiskOutcomeEnum.CHALLENGED);
        writeEvent(MEMBER_ID, HASH, MemberRiskDecisionEnum.PASS, MemberRiskOutcomeEnum.SUCCESS);

        MemberRiskEventQuery byMember = newQuery();
        byMember.setMemberId(MEMBER_ID);
        assertEquals(2, adminService.queryEventPage(byMember).list().size());

        MemberRiskEventQuery byDecision = newQuery();
        byDecision.setMemberId(MEMBER_ID);
        byDecision.setDecision(MemberRiskDecisionEnum.OTP.getValue());
        assertEquals(1, adminService.queryEventPage(byDecision).list().size());

        MemberRiskEventQuery byOutcome = newQuery();
        byOutcome.setMemberId(MEMBER_ID);
        byOutcome.setOutcome(MemberRiskOutcomeEnum.SUCCESS.getValue());
        assertEquals(1, adminService.queryEventPage(byOutcome).list().size());
    }

    @Test
    @DisplayName("🔴「只看结论不一致」—— 陪跑切生效会多打扰的就是这批人")
    void shadowDiffOnly() {
        // 生效结论放行、陪跑结论要 OTP：切过去这个人就会被多验一道
        writeEvent(MEMBER_ID, HASH, MemberRiskDecisionEnum.PASS, MemberRiskOutcomeEnum.SUCCESS,
                MemberRiskDecisionEnum.OTP);
        writeEvent(MEMBER_ID, HASH, MemberRiskDecisionEnum.PASS, MemberRiskOutcomeEnum.SUCCESS,
                MemberRiskDecisionEnum.PASS);

        MemberRiskEventQuery query = newQuery();
        query.setMemberId(MEMBER_ID);
        query.setShadowDiffOnly(true);

        List<MemberRiskEventDTO> list = adminService.queryEventPage(query).list();
        assertEquals(1, list.size());
        assertEquals(MemberRiskDecisionEnum.OTP, list.get(0).getShadowDecision());
    }

    @Test
    @DisplayName("🔴 不传时间范围时兜一个默认区间，而不是不限时间（表按月分区）")
    void defaultWindowApplied() {
        MemberRiskEventQuery query = newQuery();
        query.setMemberId(MEMBER_ID);

        adminService.queryEventPage(query);

        assertNotNull(query.getStartTime(), "没有时间条件的查询会在每个分区上各探一次索引");
        assertNotNull(query.getEndTime());
    }

    @Test
    @DisplayName("时间范围之外的查不到")
    void respectsTimeRange() {
        writeEvent(MEMBER_ID, HASH, MemberRiskDecisionEnum.PASS, MemberRiskOutcomeEnum.SUCCESS);

        MemberRiskEventQuery query = newQuery();
        query.setMemberId(MEMBER_ID);
        query.setStartTime(LocalDateTime.now().minusDays(30));
        query.setEndTime(LocalDateTime.now().minusDays(20));

        assertTrue(adminService.queryEventPage(query).list().isEmpty());
    }

    // ------------------------------------------------------------------ 详情

    @Test
    @DisplayName("详情带出命中明细，而且规则名是从规则表现取的")
    void detailCarriesHits() {
        LocalDateTime at = truncated();
        Long eventId = writeEvent(MEMBER_ID, HASH, MemberRiskDecisionEnum.OTP,
                MemberRiskOutcomeEnum.CHALLENGED, MemberRiskDecisionEnum.OTP, at);
        writeHit(eventId, at, "LOGIN_MATRIX", MemberRiskModeEnum.ENFORCE,
                MemberRiskDecisionEnum.OTP, 0, "新设备 + 陌生网络");
        writeHit(eventId, at, "NEW_COUNTRY", MemberRiskModeEnum.SHADOW, null, 50, "近期没在 美国 登录过");

        MemberRiskEventDetailDTO detail = adminService.getEventDetail(eventId, at);

        assertNotNull(detail);
        assertEquals(2, detail.hits().size());
        assertEquals("LOGIN_MATRIX", detail.hits().get(0).getRuleCode());
        assertNotNull(detail.hits().get(0).getRuleName(),
                "规则名要从规则表 join 出来 —— 命中明细只存编码，而编码不是给人看的");
        assertEquals("新设备 + 陌生网络", detail.hits().get(0).getDetail());
        // 陪跑那一条也要在，而且档位是「当时的」
        assertEquals(MemberRiskModeEnum.SHADOW, detail.hits().get(1).getMode());
    }

    @Test
    @DisplayName("一条命中都没有时详情照样出得来（绝大多数登录是这样）")
    void detailWithoutHits() {
        LocalDateTime at = truncated();
        Long eventId = writeEvent(MEMBER_ID, HASH, MemberRiskDecisionEnum.PASS,
                MemberRiskOutcomeEnum.SUCCESS, MemberRiskDecisionEnum.PASS, at);

        MemberRiskEventDetailDTO detail = adminService.getEventDetail(eventId, at);

        assertNotNull(detail);
        assertTrue(detail.hits().isEmpty());
    }

    @Test
    @DisplayName("id 对但时间不对 → 查不到（分区裁剪的代价，所以列表每行都带着时间）")
    void detailNeedsRightTime() {
        LocalDateTime at = truncated();
        Long eventId = writeEvent(MEMBER_ID, HASH, MemberRiskDecisionEnum.PASS,
                MemberRiskOutcomeEnum.SUCCESS, MemberRiskDecisionEnum.PASS, at);

        assertNull(adminService.getEventDetail(eventId, at.minusDays(1)));
    }

    // ------------------------------------------------------------------ 规则

    @Test
    @DisplayName("规则列表是全的（登录 18 + 注册 2 + 重置 2 + 资产出口 3 + 换绑 3），按【场景 → 阶段】分好组")
    void listRules() {
        List<MemberRiskRule> rules = adminService.listRules();

        assertEquals(18, rules.size(), "少一条就说明种子数据没灌全 —— 那会让 app-biz 启动失败（2026-10-17 起纯计次的 6 条移交网关限流、黑名单与设备封禁的 10 条移交网关安全闸）");

        // 场景不能交错：注册的规则夹在登录的规则中间，页面上那一列就白加了
        solvela.enums.MemberRiskEventTypeEnum scenario = null;
        java.util.Set<solvela.enums.MemberRiskEventTypeEnum> seen = new java.util.HashSet<>();
        boolean post = false;
        for (MemberRiskRule rule : rules) {
            if (rule.getEventType() != scenario) {
                assertTrue(seen.add(rule.getEventType()), "同一个场景出现了两段: " + rule.getEventType());
                scenario = rule.getEventType();
                post = false;
            }
            if (rule.getPhase() == solvela.enums.MemberRiskPhaseEnum.POST) {
                post = true;
            } else {
                assertFalse(post, "前置与后置混在一起的话，页面上就看不出一条规则是在密码前还是密码后判的");
            }
        }

        // 🔴 只有登录分前后两段（「密码对不对」把信号分成了两半）。
        //    其余三个场景都只有前置：验证码验过 / 操作提交之后就没有「再判一次」的位置了
        assertTrue(rules.stream()
                .filter(r -> r.getEventType() != solvela.enums.MemberRiskEventTypeEnum.LOGIN)
                .allMatch(r -> r.getPhase() == solvela.enums.MemberRiskPhaseEnum.PRE));

        // 资产出口与换绑那几条【都不许被可信设备跳过】—— 那正是这两个场景存在的理由
        assertTrue(rules.stream()
                .filter(r -> r.getEventType() == solvela.enums.MemberRiskEventTypeEnum.ASSET_OUT
                        || r.getEventType() == solvela.enums.MemberRiskEventTypeEnum.CONTACT_BIND)
                .allMatch(r -> Integer.valueOf(0).equals(r.getBypassByTrust())),
                "可信设备跳过的话，「一台验证过的老设备被偷了会话」这件事就永远判不出来");
    }

    @Test
    @DisplayName("🔴 保存参数要用规则自己的校验 —— 存进一个读不出来的参数等于把规则静默关掉")
    void updateValidatesParams() {
        MemberRiskRule rule = adminService.listRules().stream()
                .filter(r -> "IP_FAIL_LIMIT".equals(r.getRuleCode())).findFirst().orElseThrow();
        String original = rule.getParams();
        try {
            // tiers 的处置必须随阈值越来越严，这一份是倒着的
            assertThrows(IllegalArgumentException.class, () -> adminService.updateRule(rule.getId(),
                    MemberRiskModeEnum.ENFORCE, 0,
                    "{\"window\":\"1h\",\"tiers\":[{\"over\":5,\"do\":6},{\"over\":20,\"do\":2}]}"));

            assertThrows(IllegalArgumentException.class, () -> adminService.updateRule(rule.getId(),
                    MemberRiskModeEnum.ENFORCE, 0, "这不是 JSON"));

            // 合法的那一份存得进去
            MemberRiskRule after = adminService.updateRule(rule.getId(), MemberRiskModeEnum.ENFORCE, 0,
                    "{\"window\":\"1h\",\"tiers\":[{\"over\":7,\"do\":2},{\"over\":25,\"do\":6}]}");
            assertTrue(after.getParams().contains("\"over\": 7") || after.getParams().contains("\"over\":7"));
        } finally {
            adminService.updateRule(rule.getId(), rule.getMode(),
                    rule.getScore() == null ? 0 : rule.getScore(), original == null ? "" : original);
        }
    }

    @Test
    @DisplayName("档位改得动，而且改完缓存会跟着变（刷新间隔之内可能有延迟，这里只验库）")
    void updateMode() {
        MemberRiskRule rule = adminService.listRules().stream()
                .filter(r -> "NO_DEVICE".equals(r.getRuleCode())).findFirst().orElseThrow();
        MemberRiskModeEnum original = rule.getMode();
        try {
            MemberRiskRule after = adminService.updateRule(rule.getId(), MemberRiskModeEnum.OFF, 10, null);
            assertEquals(MemberRiskModeEnum.OFF, after.getMode());
            assertEquals(10, after.getScore());
        } finally {
            adminService.updateRule(rule.getId(), original, rule.getScore(), null);
        }
    }

    @Test
    @DisplayName("改一条不存在的规则 → 明确报错，而不是悄悄什么都不做")
    void updateMissingRule() {
        assertThrows(IllegalArgumentException.class,
                () -> adminService.updateRule(-1L, MemberRiskModeEnum.OFF, 0, null));
    }

    // ------------------------------------------------------------------ 造数

    /** 分页参数在控制器边界由 @NotNull 保证，直接调 Service 时要自己给 */
    private static MemberRiskEventQuery newQuery() {
        MemberRiskEventQuery query = new MemberRiskEventQuery();
        query.setPageNum(1L);
        query.setPageSize(20L);
        return query;
    }

    /** 流水的 create_time 是秒级的（命中明细要和它精确相等） */
    private static LocalDateTime truncated() {
        return LocalDateTime.now().withNano(0);
    }

    private Long writeEvent(Long memberId, String hash, MemberRiskDecisionEnum decision,
                            MemberRiskOutcomeEnum outcome) {
        return writeEvent(memberId, hash, decision, outcome, decision, truncated());
    }

    private Long writeEvent(Long memberId, String hash, MemberRiskDecisionEnum decision,
                            MemberRiskOutcomeEnum outcome, MemberRiskDecisionEnum shadow) {
        return writeEvent(memberId, hash, decision, outcome, shadow, truncated());
    }

    private Long writeEvent(Long memberId, String hash, MemberRiskDecisionEnum decision,
                            MemberRiskOutcomeEnum outcome, MemberRiskDecisionEnum shadow,
                            LocalDateTime createTime) {
        MemberRiskEvent event = new MemberRiskEvent();
        event.setEventType(MemberRiskEventTypeEnum.LOGIN);
        event.setMemberId(memberId);
        event.setIdentityHashHex(hash);
        event.setLoginType(2);
        event.setDeviceId("dev-admin-test");
        event.setDeviceTrust(1);
        event.setIpFamiliar(0);
        event.setDormant(0);
        event.setClientIp("1.2.3.4");
        event.setIpRegion("中国|广东省|深圳市|电信");
        event.setCaptchaPassed(1);
        event.setPreScore(0);
        event.setPostScore(0);
        event.setShadowScore(0);
        event.setDecision(decision);
        event.setShadowDecision(shadow);
        event.setOutcome(outcome);
        event.setCostMs(3);
        event.setTraceId("admin-test");
        event.setCreateTime(createTime);
        eventDao.insertEvent(event);
        return event.getId();
    }

    private void writeHit(Long eventId, LocalDateTime at, String ruleCode, MemberRiskModeEnum mode,
                          MemberRiskDecisionEnum decision, int score, String detail) {
        MemberRiskHit hit = new MemberRiskHit();
        hit.setEventId(eventId);
        hit.setRuleCode(ruleCode);
        hit.setMode(mode);
        hit.setDecision(decision);
        hit.setScore(score);
        hit.setDetail(detail);
        hit.setError(0);
        hit.setCreateTime(at);
        hitDao.insertHits(List.of(hit));
        assertFalse(hit.getRuleCode().isBlank());
    }
}
