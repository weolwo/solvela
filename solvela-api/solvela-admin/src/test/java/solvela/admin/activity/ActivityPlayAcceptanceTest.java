package solvela.admin.activity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.ActivityDrawCmd;
import solvela.marketing.api.ActivityRuleView;
import solvela.marketing.api.DrawRejectReason;
import solvela.marketing.api.DrawResultView;
import solvela.exception.BusinessException;
import solvela.scriptengine.service.ScriptRefService;
import solvela.scriptengine.spi.ScriptRefPoint;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C 端「参与活动」链路验收：<b>活动校验 → 玩法编排脚本 → 抽奖引擎</b>。
 *
 * <p>这条链路横跨活动域、脚本引擎、抽奖引擎三层，而且中间有两处
 * <b>坏掉时不会报错、只会悄悄变行为</b>的地方（脚本决定的奖池有没有真的被用上、
 * 数据截止时间有没有真的挡住参与），所以必须有会失败的测试盯着。
 *
 * <p>⚠️ <b>本测试不覆盖真正中奖的那一段</b>。中奖需要奖池、奖项、映射、库存四套配置，
 * 那是 {@code 抽奖模块-联调造数.sql} 的活。这里验证的是「请求能不能正确地走到抽奖引擎门口，
 * 以及在到达之前该被挡住的有没有被挡住」—— 引擎门内的行为由 marketing 自己的测试负责。
 *
 * <p>造数全部自己建、自己删，不依赖任何造数脚本，也不碰真实业务数据。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class ActivityPlayAcceptanceTest {

    /** 刻意用真实业务不会出现的编码，避免误挂到线上活动 */
    private static final String ACTIVITY_CODE = "__PLAY_ACCEPTANCE_TEST__";

    /** 样例编排脚本，见 scripts/activity/draw_play.ql */
    private static final String PLAY_SCRIPT = "activity/draw_play";

    /** 准入判定脚本 —— 场景是 ACTIVITY_RULE，用来验证挂载点的场景守卫 */
    private static final String ENTRY_SCRIPT = "activity/basic_entry";

    /** 样例脚本在 activityType=DRAW 且 tier!=NEW 时算出来的奖池 */
    private static final String EXPECTED_POOL = "DRAW_POOL_NORMAL";

    private static final long MEMBER_ID = 9999999999L;

    @Autowired
    private ActivityApi activityApi;

    @Autowired
    private ScriptRefService scriptRefService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        // 会员必须真实存在：抽奖引擎会用会员号换账号（白名单判定、流水快照都要用）
        jdbcTemplate.update("""
                INSERT INTO t_member (member_id, member_name, nickname, status)
                VALUES (?, ?, ?, 1)
                """, MEMBER_ID, "ZzPlayAcceptance", "参与链路验收");

        // 🔴 前提条件必须真实成立：样例脚本算出来的奖池在库里【不能】存在，
        // 否则这一轮抽奖会真的走进引擎门内，测试断言的东西就变了。
        // 本项目已经空过三次「前提不成立时通过和空过分不出来」，所以这里显式确认。
        Integer poolCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_prize_pool_config WHERE pool_code = ?", Integer.class, EXPECTED_POOL);
        assertEquals(0, poolCount, () -> String.format(
                "前提不成立：库里已经存在奖池 [%s]，而本测试假设它不存在。"
                        + "请把 scripts/activity/draw_play.ql 里的样例奖池编码改成一个不会撞的值。", EXPECTED_POOL));
    }

    @AfterEach
    void cleanUp() {
        scriptRefService.unbind(ScriptRefPoint.ACTIVITY_PLAY, ACTIVITY_CODE);
        jdbcTemplate.update("DELETE FROM t_activity_config WHERE activity_code = ?", ACTIVITY_CODE);
        jdbcTemplate.update("DELETE FROM t_member WHERE member_id = ?", MEMBER_ID);
    }

    // =====================================================================
    // 到不了脚本的那几种：全部是 reject，不是异常
    // =====================================================================

    @Test
    @DisplayName("活动不存在 → ACTIVITY_NOT_FOUND，不抛异常")
    void 活动不存在() {
        DrawResultView result = activityApi.draw(cmd());

        assertEquals(DrawRejectReason.ACTIVITY_NOT_FOUND, result.reject(),
                "拿一个不存在的活动编码来访问是完全正常的事（链接过期、地址栏被改），"
                        + "用异常表达它，跨进程后会变成 5xx，监控上多一堆假的服务端错误");
        assertFalse(result.accepted());
        assertFalse(result.hit());
    }

    @Test
    @DisplayName("活动未上线 → ACTIVITY_NOT_OPEN")
    void 活动未上线() {
        insertActivity(0, LocalDateTime.now().minusDays(1), null, LocalDateTime.now().plusDays(7));

        assertEquals(DrawRejectReason.ACTIVITY_NOT_OPEN, activityApi.draw(cmd()).reject());
    }

    @Test
    @DisplayName("🔴 已过 data_end_time 但没过 end_time → 挡住参与")
    void 数据截止之后不再受理参与() {
        // 数据昨天截止，活动还有一周才结束 —— 这正是加 data_end_time 那一列要表达的形态
        insertActivity(1,
                LocalDateTime.now().minusDays(30),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(7));

        DrawResultView result = activityApi.draw(cmd());

        assertEquals(DrawRejectReason.ACTIVITY_NOT_OPEN, result.reject(),
                "参与的判据必须是 data_end_time 而不是 end_time。判成 end_time 的话，"
                        + "数据截止后用户还能继续抽，而那段时间本来是留给发奖和对账的");
    }

    @Test
    @DisplayName("🔴 数据截止之后活动仍然可见、仍然可领奖")
    void 数据截止之后仍可查看与领奖() {
        insertActivity(1,
                LocalDateTime.now().minusDays(30),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(7));

        ActivityRuleView view = activityApi.getActivityRule(ACTIVITY_CODE);

        assertNotNull(view, "数据截止不等于活动结束，详情页必须还打得开");
        LocalDateTime now = LocalDateTime.now();
        assertFalse(view.joinable(now), "已过数据截止时间，不该还能参与");
        assertTrue(view.claimable(now), "还没到活动结束时间，已中的奖必须还能领 —— 这正是拆出这一列的理由");
    }

    @Test
    @DisplayName("活动在窗内但没挂编排脚本 → NO_PLAY_SCRIPT，不是异常")
    void 没挂脚本() {
        insertOnlineActivity();

        assertEquals(DrawRejectReason.NO_PLAY_SCRIPT, activityApi.draw(cmd()).reject(),
                "运营配置没做完是预期内的情况，不该让用户看到「服务开小差了」");
    }

    // =====================================================================
    // 走进脚本的那几种
    // =====================================================================

    @Test
    @DisplayName("🔴 脚本算出的奖池真的被用上了 —— 请求走到了抽奖引擎门口")
    void 脚本决定的奖池被传到了引擎() {
        insertOnlineActivity();
        scriptRefService.bind(ScriptRefPoint.ACTIVITY_PLAY, ACTIVITY_CODE, PLAY_SCRIPT, "acceptance-test");

        DrawResultView result = activityApi.draw(cmd());

        // 奖池不存在（setUp 已确认前提），所以引擎回的是 POOL_NOT_FOUND。
        // 拿到这个值恰恰证明整条链路是通的：活动校验过了 → 脚本跑了 → 脚本算出的
        // poolCode 被传进了引擎 → 引擎按那个编码查了库。
        // 如果链路断在任何一处，这里拿到的会是别的 reject 或者一个异常。
        assertEquals(DrawRejectReason.POOL_NOT_FOUND, result.reject(),
                "期望链路走到抽奖引擎并因奖池不存在被拒；拿到别的值说明中间某一段断了");
    }

    @Test
    @DisplayName("params 缺 tier 时脚本照常跑 —— 空 Map 由域内补，不是让脚本判 null")
    void 缺省参数不会让脚本炸() {
        insertOnlineActivity();
        scriptRefService.bind(ScriptRefPoint.ACTIVITY_PLAY, ACTIVITY_CODE, PLAY_SCRIPT, "acceptance-test");

        DrawResultView result = activityApi.draw(
                new ActivityDrawCmd(ACTIVITY_CODE, MEMBER_ID, "req-" + System.nanoTime(), null));

        assertEquals(DrawRejectReason.POOL_NOT_FOUND, result.reject(),
                "params 传 null 时域内会换成空 Map，脚本不该因此拿不到必填变量而失败");
    }

    @Test
    @DisplayName("非抽奖类活动挂了抽奖编排脚本 → 报错点名脚本，而不是静默不发奖")
    void 挂错类型的活动会报错() {
        // 样例脚本对 activityType != DRAW 直接 return null，
        // 而场景契约把 null 视为违约 —— 「忘了覆盖分支」伪装成「判定不通过」是最难发现的故障
        insertActivity(1, LocalDateTime.now().minusDays(1), null, LocalDateTime.now().plusDays(7), "TASK");
        scriptRefService.bind(ScriptRefPoint.ACTIVITY_PLAY, ACTIVITY_CODE, PLAY_SCRIPT, "acceptance-test");

        BusinessException e = assertThrows(BusinessException.class, () -> activityApi.draw(cmd()));

        assertTrue(e.getMessage().contains(PLAY_SCRIPT) || e.getMessage().contains("没有返回值"),
                "报错必须点名是哪个脚本，否则运营无从下手。实际: " + e.getMessage());
    }

    @Test
    @DisplayName("准入脚本挂到玩法编排挂载点上会被拒绝 —— 场景守卫")
    void 场景不匹配的脚本挂不上去() {
        insertOnlineActivity();

        BusinessException e = assertThrows(BusinessException.class, () -> scriptRefService.bind(
                ScriptRefPoint.ACTIVITY_PLAY, ACTIVITY_CODE, ENTRY_SCRIPT, "acceptance-test"));

        assertTrue(e.getMessage().contains("场景不匹配"), "实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("ACTIVITY_PLAY") && e.getMessage().contains("ACTIVITY_RULE"),
                "报错要同时点名两个场景，否则不知道是哪边挂错了。实际: " + e.getMessage());
    }

    @Test
    @DisplayName("活动不存在时 getActivityRule 返回 null，不抛异常")
    void 查不到的活动返回null() {
        assertNull(activityApi.getActivityRule(ACTIVITY_CODE));
    }

    // =====================================================================

    private ActivityDrawCmd cmd() {
        return new ActivityDrawCmd(ACTIVITY_CODE, MEMBER_ID,
                "req-" + System.nanoTime(), Map.of("tier", "NORMAL"));
    }

    private void insertOnlineActivity() {
        insertActivity(1, LocalDateTime.now().minusDays(1), null, LocalDateTime.now().plusDays(7));
    }

    private void insertActivity(int status, LocalDateTime start, LocalDateTime dataEnd, LocalDateTime end) {
        insertActivity(status, start, dataEnd, end, "DRAW");
    }

    private void insertActivity(int status, LocalDateTime start, LocalDateTime dataEnd,
                                LocalDateTime end, String activityType) {
        jdbcTemplate.update("""
                INSERT INTO t_activity_config
                    (activity_code, activity_name, activity_type, status, start_time, data_end_time, end_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, ACTIVITY_CODE, "参与链路验收活动", activityType, status, start, dataEnd, end);
    }
}
