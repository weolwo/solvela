package solvela.task.record.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.TaskRecordStatusEnum;
import solvela.task.record.domain.query.TaskRecordQuery;

import java.io.InputStream;
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
 * 任务记录状态在 SQL 里的每一处，都必须和 {@link TaskRecordStatusEnum} 对得上。
 *
 * <p>状态值写成 {@code ${@solvela.enums.TaskRecordStatusEnum@X.value}}：常量改名时编译器不报错，
 * 只会运行期求值失败 —— 表现是任务永远达不了标、奖永远发不出去。
 * 这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库），断言四条闸门（达标 / 发奖 / 过期 / 管理端禁用）
 * 的迁移都在 {@link TaskRecordStatusEnum#canTransitTo} 里，且状态机的每条边都有闸门。
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class TaskRecordStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("SET\\s+status\\s*=\\s*(\\d+)");

    /** WHERE 之后的第一个 status 条件 —— 闸门的前置状态 */
    private static final Pattern WHERE_STATUS = Pattern.compile("WHERE\\b.*?\\bstatus\\s*=\\s*(\\d+)");

    private static final String NS = TaskRecordDao.class.getName() + ".";

    private static final List<String> GATES =
            List.of("markCompleted", "markDispatched", "expireRecordBatch", "expireByAdmin");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/marketing/TaskRecordMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 闸门 SQL 的迁移与枚举状态机一致：闸门都合法，每条边都有闸门")
    void 闸门与状态机一致() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES) {
            String sql = sqlOf(id);
            TaskRecordStatusEnum from = statusOf(WHERE_STATUS, sql, id);
            TaskRecordStatusEnum to = statusOf(SET_STATUS, sql, id);
            assertTrue(from.canTransitTo(to), id + " 的迁移 " + from + "->" + to + " 不在状态机里");
            fromSql.add(from + "->" + to);
        }
        Set<String> fromEnum = new HashSet<>();
        for (TaskRecordStatusEnum from : TaskRecordStatusEnum.values()) {
            for (TaskRecordStatusEnum to : TaskRecordStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertFalse(fromEnum.isEmpty(), "状态机被清空了");
        assertEquals(fromEnum, fromSql, "状态机里有边没有对应的闸门 SQL，或反过来");
    }

    @Test
    @DisplayName("每条闸门的两端；禁用与过期任务一样只认进行中")
    void 每条闸门的两端() {
        assertGate("markCompleted", TaskRecordStatusEnum.RUNNING, TaskRecordStatusEnum.COMPLETED);
        assertGate("markDispatched", TaskRecordStatusEnum.COMPLETED, TaskRecordStatusEnum.DISPATCHED);
        assertGate("expireRecordBatch", TaskRecordStatusEnum.RUNNING, TaskRecordStatusEnum.EXPIRED);
        assertGate("expireByAdmin", TaskRecordStatusEnum.RUNNING, TaskRecordStatusEnum.EXPIRED);
    }

    @Test
    @DisplayName("推进进度的两条 UPDATE 只认进行中：已达标的记录不再接受事件")
    void 推进只认进行中() {
        for (String id : List.of("advanceMetric", "overwriteMetric", "countExpirableRecord")) {
            assertEquals(TaskRecordStatusEnum.RUNNING, statusOf(WHERE_STATUS, sqlOf(id), id), id);
        }
    }

    @Test
    @DisplayName("漏斗统计给每个状态都留了桶，引用全部求值成功")
    void 漏斗统计每个状态都有桶() {
        String sql = sqlOf("selectFunnel");
        for (TaskRecordStatusEnum s : TaskRecordStatusEnum.values()) {
            assertTrue(sql.contains("SUM(status = " + s.getValue() + ")"), () -> s + " 在漏斗统计里没有桶：" + sql);
        }
        sqlOf("selectTaskStat");
    }

    private static void assertGate(String id, TaskRecordStatusEnum from, TaskRecordStatusEnum to) {
        String sql = sqlOf(id);
        assertEquals(from, statusOf(WHERE_STATUS, sql, id), () -> id + " 的前置状态不对，SQL: " + sql);
        assertEquals(to, statusOf(SET_STATUS, sql, id), () -> id + " 的目标状态不对，SQL: " + sql);
    }

    private static TaskRecordStatusEnum statusOf(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status 条件。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(TaskRecordStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 TaskRecordStatusEnum"));
    }

    /** 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，常量拼错或改名会在这里抛异常 */
    private static String sqlOf(String id) {
        Map<String, Object> param = new HashMap<>();
        param.put("queryForm", new TaskRecordQuery());
        String sql = configuration.getMappedStatement(NS + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
