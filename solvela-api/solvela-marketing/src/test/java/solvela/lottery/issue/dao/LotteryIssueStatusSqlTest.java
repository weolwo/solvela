package solvela.lottery.issue.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.IssueStatusEnum;
import solvela.enums.LotteryConfigStatusEnum;
import solvela.lottery.issue.domain.query.LotteryIssueQuery;

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
 * 期号状态在 SQL 里的每一处，都必须和 {@link IssueStatusEnum} 对得上。
 *
 * <p>状态值写成 {@code ${@solvela.enums.IssueStatusEnum@X.value}}：常量改名时编译器不报错，只会运行期求值失败 ——
 * 表现是开奖点了没反应，或者一个号都发不出去。这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库），断言：
 * <ul>
 *   <li>开奖两条闸门与 {@link IssueStatusEnum#canTransitTo} 一条不多、一条不少；</li>
 *   <li>发号的已发数 +1 与删除都只认待开奖（删除还要零发号）；</li>
 *   <li>售卖状态、逾期、今日开奖三段表达式里的状态引用都求值成功。</li>
 * </ul>
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class LotteryIssueStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_STATUS = Pattern.compile("where\\b.*?\\bstatus\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final String NS = LotteryIssueDao.class.getName() + ".";

    private static final List<String> GATES = List.of("startSettle", "finishSettle");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/marketing/LotteryIssueMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 开奖两条闸门：待开奖 → 核销中 → 已开奖，与枚举状态机一条不多、一条不少")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES) {
            String sql = sqlOf(id);
            fromSql.add(statusOf(WHERE_STATUS, sql, id) + "->" + statusOf(SET_STATUS, sql, id));
        }
        Set<String> fromEnum = new HashSet<>();
        for (IssueStatusEnum from : IssueStatusEnum.values()) {
            for (IssueStatusEnum to : IssueStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertEquals(GATES.size(), fromSql.size(), "两条闸门应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql, "LotteryIssueDao 的闸门与 IssueStatusEnum#canTransitTo 对不上");
        assertTrue(sqlOf("startSettle").contains("winning_number = ?"), "开奖号码要在抢到闸门那一刻随状态一起定案");
    }

    @Test
    @DisplayName("🔴 发号 +1 只认待开奖：与开奖抢同一行锁，开奖开始之后发不出号")
    void 发号只认待开奖() {
        String sql = sqlOf("increaseSoldCount");
        assertTrue(sql.contains("i.status = " + IssueStatusEnum.WAIT.getValue()), "increaseSoldCount 必须带「仍是待开奖」：" + sql);
        assertTrue(sql.contains("i.sold_count < c.total_count"), "防超发的条件不能丢：" + sql);
    }

    @Test
    @DisplayName("🔴 删除只删零发号的待开奖空期")
    void 删除只删空期() {
        String sql = sqlOf("deleteUnsold");
        assertEquals(IssueStatusEnum.WAIT, statusOf(WHERE_STATUS, sql, "deleteUnsold"));
        assertTrue(sql.contains("sold_count = 0"), "删除必须带零发号条件：" + sql);
    }

    @Test
    @DisplayName("售卖状态、逾期、今日开奖里的状态引用都求值成功")
    void 查询表达式能渲染() {
        String sql = sqlOf("queryPage");
        assertTrue(sql.contains("c.status <> " + LotteryConfigStatusEnum.ONLINE.getValue()), sql);
        assertTrue(sql.contains("i.status <> " + IssueStatusEnum.WAIT.getValue()), sql);
        sqlOf("queryList");
    }

    private static IssueStatusEnum statusOf(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status 条件。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(IssueStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 IssueStatusEnum"));
    }

    /** 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，常量拼错或改名会在这里抛异常 */
    private static String sqlOf(String id) {
        Map<String, Object> param = new HashMap<>();
        LotteryIssueQuery query = new LotteryIssueQuery();
        param.put("queryForm", query);
        param.put("now", LocalDateTime.now());
        String sql = configuration.getMappedStatement(NS + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
