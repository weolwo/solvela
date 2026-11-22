package solvela.mall.order.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.MallOrderStatusEnum;
import solvela.mall.order.domain.query.MallOrderQuery;

import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单状态在 SQL 里的每一处，都必须和 {@link MallOrderStatusEnum} 对得上。
 *
 * <h3>为什么需要这个测试</h3>
 * 状态迁移写在 SQL 的 WHERE 里（这是对的：条件 UPDATE 才能在并发下当闸门用），
 * 状态值写成 {@code ${@solvela.enums.MallOrderStatusEnum@X.value}}。枚举<b>取值</b>改了 SQL 自动跟着变，
 * 但枚举<b>常量改名</b>时编译器不报错，只会在运行期 OGNL 求值失败 ——
 * 表现是「兑换成功了，但东西永远发不出去」。
 *
 * <p>所以这里不读注解原文，而是让 MyBatis 真的把 SQL 渲染出来（不连库），
 * 断言的是<b>发给数据库的那一份</b>：
 * <ul>
 *   <li>五条闸门 SQL 的迁移，与 {@link MallOrderStatusEnum#canTransitTo} 的边一条不多、一条不少；</li>
 *   <li>「已结清」口径（{@code paid_statuses} 片段与 {@code selectSettledBetween}）
 *       与 {@link MallOrderStatusEnum#isSettled} 一致。</li>
 * </ul>
 *
 * @Date 2026-09-05
 */
class MallOrderStatusSqlTest {

    /** {@code SET status = 20} 里那个数 */
    private static final Pattern SET_STATUS = Pattern.compile("SET\\s+status\\s*=\\s*(\\d+)");

    /** {@code AND status = 10} 里那个数 —— 这是闸门的前置状态 */
    private static final Pattern WHERE_STATUS = Pattern.compile("AND\\s+status\\s*=\\s*(\\d+)");

    /** {@code status NOT IN (0, 40)} 里那组数 —— 「没结清」的状态 */
    private static final Pattern NOT_IN = Pattern.compile("status\\s+NOT\\s+IN\\s*\\(([\\d,\\s]+)\\)");

    private static final String NS = MallOrderDao.class.getName() + ".";

    private static final List<String> GATES =
            List.of("markPaid", "markCancelled", "markFulfilling", "markFinished", "markFailed");

    private static Configuration configuration;

    /**
     * 解析 XML 时 MyBatis 会按 namespace 顺带把 {@link MallOrderDao} 的注解 SQL 也注册进来。
     * 用裸 {@link Configuration} 而不起 Spring：渲染 SQL 用不到数据源与类型处理器。
     */
    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/mall/MallOrderMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 五条闸门 SQL 的迁移，与枚举状态机一条不多、一条不少")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String method : GATES) {
            String sql = sqlOf(method);
            fromSql.add(edge(statusOf(WHERE_STATUS, sql, method), statusOf(SET_STATUS, sql, method)));
        }

        Set<String> fromEnum = new HashSet<>();
        for (MallOrderStatusEnum from : MallOrderStatusEnum.values()) {
            for (MallOrderStatusEnum to : MallOrderStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(edge(from, to));
                }
            }
        }

        // 空对空也相等：先确认两边真的有东西，否则状态机被清空时本测试会永远绿着
        assertEquals(GATES.size(), fromSql.size(), "五条闸门 SQL 应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql,
                "MallOrderDao 的闸门与 MallOrderStatusEnum#canTransitTo 对不上。"
                        + "加了新迁移要两边一起加：枚举里加边，DAO 里加带旧状态条件的 UPDATE");
    }

    @Test
    @DisplayName("每条闸门的两端：失败时直接指出是哪一条")
    void 每条闸门的两端() {
        assertGate("markPaid", MallOrderStatusEnum.UNPAID, MallOrderStatusEnum.PENDING);
        assertGate("markCancelled", MallOrderStatusEnum.UNPAID, MallOrderStatusEnum.CANCELLED);
        assertGate("markFulfilling", MallOrderStatusEnum.PENDING, MallOrderStatusEnum.FULFILLING);
        assertGate("markFinished", MallOrderStatusEnum.FULFILLING, MallOrderStatusEnum.FINISHED);
        assertGate("markFailed", MallOrderStatusEnum.FULFILLING, MallOrderStatusEnum.FAILED);
    }

    @Test
    @DisplayName("🔴 「已结清」在 SQL 里的两处，与 isSettled 是同一条口径")
    void 结清口径与枚举一致() {
        Set<MallOrderStatusEnum> notSettled = Arrays.stream(MallOrderStatusEnum.values())
                .filter(s -> !s.isSettled())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MallOrderStatusEnum.class)));
        assertFalse(notSettled.isEmpty(), "isSettled 对所有状态都为真，口径被改坏了");

        assertEquals(notSettled, notInOf(sqlOf("selectSettledBetween")), "selectSettledBetween");
        // paid_statuses 片段没有独立的语句，借用引用了它的排行榜查询
        assertEquals(notSettled, notInOf(sqlOf("queryCommodityRank")), "MallOrderMapper.xml 的 paid_statuses");
    }

    @Test
    @DisplayName("超时扫描只捞待支付")
    void 超时扫描只捞待支付() {
        String sql = sqlOf("selectExpiredUnpaid");
        Matcher matcher = Pattern.compile("WHERE\\s+status\\s*=\\s*(\\d+)").matcher(sql);
        assertTrue(matcher.find(), "selectExpiredUnpaid 里找不到 status 条件：" + sql);
        assertEquals(MallOrderStatusEnum.UNPAID.getValue(), Integer.valueOf(matcher.group(1)));
    }

    @Test
    @DisplayName("统计 SQL 里的状态引用全部求值成功")
    void 统计SQL能渲染() {
        String sql = sqlOf("queryStat");
        assertTrue(sql.contains("status = " + MallOrderStatusEnum.FINISHED.getValue()), sql);
        assertTrue(sql.contains("status = " + MallOrderStatusEnum.FAILED.getValue()), sql);
    }

    @Test
    @DisplayName("🔴 闸门 SQL 都不许自己写 update_time —— 那一列归数据库时钟管")
    void 不许手填更新时间() {
        for (String method : GATES) {
            /*
             * 铁律 9：create_time / update_time 只认数据库时钟。
             * t_mall_order.update_time 是 ON UPDATE CURRENT_TIMESTAMP，自己会填。
             * 手填的害处不是多写一列，是应用服务器和数据库的时钟一旦有偏差，
             * 按时间排的运营列表就会乱序，而没人会想到去查时钟。
             */
            assertFalse(sqlOf(method).toLowerCase().contains("update_time"),
                    method + " 的 SQL 里出现了 update_time，那一列该由数据库自己填");
        }
    }

    /* ---------------- 工具 ---------------- */

    private static void assertGate(String method, MallOrderStatusEnum from, MallOrderStatusEnum to) {
        String sql = sqlOf(method);
        assertEquals(from, statusOf(WHERE_STATUS, sql, method), () -> method + " 的前置状态不对，SQL: " + sql);
        assertEquals(to, statusOf(SET_STATUS, sql, method), () -> method + " 的目标状态不对，SQL: " + sql);
    }

    private static String edge(MallOrderStatusEnum from, MallOrderStatusEnum to) {
        return from + "->" + to;
    }

    private static MallOrderStatusEnum statusOf(Pattern pattern, String sql, String method) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> method + " 的 SQL 里找不到 status 条件。"
                + "改写这条 SQL 时请连同本测试一起改 —— 别把它删掉了事。SQL: " + sql);
        return byValue(Integer.parseInt(matcher.group(1)), method);
    }

    private static Set<MallOrderStatusEnum> notInOf(String sql) {
        Matcher matcher = NOT_IN.matcher(sql);
        assertTrue(matcher.find(), () -> "SQL 里找不到 status NOT IN (...)：" + sql);
        return Arrays.stream(matcher.group(1).split(","))
                .map(v -> byValue(Integer.parseInt(v.trim()), sql))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MallOrderStatusEnum.class)));
    }

    private static MallOrderStatusEnum byValue(int value, String where) {
        return Arrays.stream(MallOrderStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError("SQL 里的状态值 " + value + " 不是任何一个 MallOrderStatusEnum：" + where));
    }

    /**
     * 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，
     * 枚举常量拼错或改名会在这里抛 OGNL 异常。
     */
    private static String sqlOf(String statementId) {
        Map<String, Object> param = new HashMap<>();
        param.put("queryForm", new MallOrderQuery());
        param.put("topN", 10);
        String sql = configuration.getMappedStatement(NS + statementId).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> statementId + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
