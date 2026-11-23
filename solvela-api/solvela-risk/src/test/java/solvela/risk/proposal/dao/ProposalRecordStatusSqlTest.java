package solvela.risk.proposal.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.ProposalStatusEnum;
import solvela.risk.proposal.domain.query.ProposalRecordQuery;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提案状态在 SQL 里的每一处，都必须和 {@link ProposalStatusEnum} 对得上。
 *
 * <p>状态值写成 {@code ${@solvela.enums.ProposalStatusEnum@X.value}}：常量改名时编译器不报错，
 * 只会运行期求值失败 —— 表现是提案永远停在待执行，或审批点了没反应。
 * 这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库），断言：
 * <ul>
 *   <li>九条闸门的迁移与 {@link ProposalStatusEnum#canTransitTo} 的边一条不多、一条不少；</li>
 *   <li>审批闸门写的是自己那一级的审批人列，一审不写进二审的列；</li>
 *   <li>统计与卡单扫描里的状态引用全部求值成功。</li>
 * </ul>
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class ProposalRecordStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_STATUS = Pattern.compile("and\\s+status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final String NS = ProposalRecordDao.class.getName() + ".";

    /** 闸门 → [前置状态, 目标状态, 写哪一级审批人（非审批闸门为 null）] */
    private static final Map<String, Object[]> GATES = Map.of(
            "claimForExecution", gate(ProposalStatusEnum.PENDING_EXECUTE, ProposalStatusEnum.EXECUTING, null),
            "markSucceeded", gate(ProposalStatusEnum.EXECUTING, ProposalStatusEnum.SUCCESS, null),
            "markFailed", gate(ProposalStatusEnum.EXECUTING, ProposalStatusEnum.FAILED, null),
            "markFailedUnclaimed", gate(ProposalStatusEnum.PENDING_EXECUTE, ProposalStatusEnum.FAILED, null),
            "passFirstReview", gate(ProposalStatusEnum.FIRST_REVIEW, ProposalStatusEnum.PENDING_EXECUTE, "first"),
            "escalateToSecondReview", gate(ProposalStatusEnum.FIRST_REVIEW, ProposalStatusEnum.SECOND_REVIEW, "first"),
            "passSecondReview", gate(ProposalStatusEnum.SECOND_REVIEW, ProposalStatusEnum.PENDING_EXECUTE, "second"),
            "rejectAtFirstReview", gate(ProposalStatusEnum.FIRST_REVIEW, ProposalStatusEnum.REJECTED, "first"),
            "rejectAtSecondReview", gate(ProposalStatusEnum.SECOND_REVIEW, ProposalStatusEnum.REJECTED, "second"));

    private static final List<String> STAT_QUERIES = List.of("selectFunnel", "selectAssetStat", "selectSourceStat",
            "selectBlockReasonStat", "countStuckDispatch", "selectStuckDispatchSample");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/risk/ProposalRecordMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 九条闸门 SQL 的迁移，与枚举状态机一条不多、一条不少")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES.keySet()) {
            String sql = sqlOf(id);
            fromSql.add(edge(statusOf(WHERE_STATUS, sql, id), statusOf(SET_STATUS, sql, id)));
        }

        Set<String> fromEnum = new HashSet<>();
        for (ProposalStatusEnum from : ProposalStatusEnum.values()) {
            for (ProposalStatusEnum to : ProposalStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(edge(from, to));
                }
            }
        }

        // 空对空也相等：先确认两边真的有东西，否则状态机被清空时本测试会永远绿着
        assertEquals(GATES.size(), fromSql.size(), "九条闸门 SQL 应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql,
                "ProposalRecordDao 的闸门与 ProposalStatusEnum#canTransitTo 对不上。"
                        + "加了新迁移要两边一起加：枚举里加边，DAO 里加带旧状态条件的 UPDATE");
    }

    @Test
    @DisplayName("每条闸门的两端与审批人列：失败时直接指出是哪一条")
    void 每条闸门的两端() {
        GATES.forEach((id, expected) -> {
            String sql = sqlOf(id);
            assertEquals(expected[0], statusOf(WHERE_STATUS, sql, id), () -> id + " 的前置状态不对，SQL: " + sql);
            assertEquals(expected[1], statusOf(SET_STATUS, sql, id), () -> id + " 的目标状态不对，SQL: " + sql);
            String level = (String) expected[2];
            if (level == null) {
                assertFalse(sql.contains("reviewer"), () -> id + " 不是审批闸门，不该写审批人：" + sql);
                return;
            }
            String other = level.equals("first") ? "second" : "first";
            assertTrue(sql.contains(level + "_reviewer = ?") && sql.contains(level + "_review_time = now()"),
                    () -> id + " 应写 " + level + " 级的审批人与审批时间：" + sql);
            assertFalse(sql.contains(other + "_reviewer"), () -> id + " 写到了 " + other + " 级的审批人列：" + sql);
        });
    }

    @Test
    @DisplayName("🔴 闸门 SQL 都不许自己写 update_time —— 那一列归数据库时钟管")
    void 不许手填更新时间() {
        for (String id : GATES.keySet()) {
            assertFalse(sqlOf(id).toLowerCase().contains("update_time"),
                    id + " 的 SQL 里出现了 update_time，t_proposal_record.update_time 是 ON UPDATE CURRENT_TIMESTAMP");
        }
    }

    @Test
    @DisplayName("统计与卡单扫描里的状态引用全部求值成功，卡单扫描只捞待执行与执行中")
    void 统计SQL能渲染() {
        for (String id : STAT_QUERIES) {
            sqlOf(id);
        }
        String stuck = "status IN (" + ProposalStatusEnum.PENDING_EXECUTE.getValue() + ", "
                + ProposalStatusEnum.EXECUTING.getValue() + ")";
        assertTrue(sqlOf("countStuckDispatch").contains(stuck), sqlOf("countStuckDispatch"));
        assertTrue(sqlOf("selectStuckDispatchSample").contains(stuck), sqlOf("selectStuckDispatchSample"));
        assertTrue(sqlOf("selectBlockReasonStat").contains("status = " + ProposalStatusEnum.RISK_BLOCKED.getValue()),
                sqlOf("selectBlockReasonStat"));
    }

    /* ---------------- 工具 ---------------- */

    private static Object[] gate(ProposalStatusEnum from, ProposalStatusEnum to, String reviewerLevel) {
        return new Object[]{from, to, reviewerLevel};
    }

    private static String edge(ProposalStatusEnum from, ProposalStatusEnum to) {
        return from + "->" + to;
    }

    private static ProposalStatusEnum statusOf(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status 条件。"
                + "改写这条 SQL 时请连同本测试一起改 —— 别把它删掉了事。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(ProposalStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 ProposalStatusEnum"));
    }

    /** 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，常量拼错或改名会在这里抛异常 */
    private static String sqlOf(String id) {
        Map<String, Object> param = new HashMap<>();
        param.put("queryForm", new ProposalRecordQuery());
        param.put("now", LocalDateTime.now());
        param.put("minutes", 30);
        param.put("limit", 10);
        String sql = configuration.getMappedStatement(NS + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
