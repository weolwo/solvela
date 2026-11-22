package solvela.member.entitlement.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.EntitlementGrantStatusEnum;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权益发放状态在 SQL 里的每一处，都必须和 {@link EntitlementGrantStatusEnum} 对得上。
 *
 * <p>状态值写成 {@code ${@solvela.enums.EntitlementGrantStatusEnum@X.value}}：常量改名时编译器不报错，
 * 只会运行期求值失败 —— 表现是谁都领不了权益，过期任务也一行都改不动。
 * 这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库），断言领取 / 过期两条闸门
 * 与 {@link EntitlementGrantStatusEnum#canTransitTo} 一条不多、一条不少。
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class EntitlementGrantStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("SET\\s+status\\s*=\\s*(\\d+)");

    /** WHERE 之后的第一个 status 条件 —— 闸门的前置状态 */
    private static final Pattern WHERE_STATUS = Pattern.compile("WHERE\\b.*?\\bstatus\\s*=\\s*(\\d+)");

    private static final String NS = GradeEntitlementGrantDao.class.getName() + ".";

    private static final List<String> GATES = List.of("tryClaim", "expirePending");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/member/GradeEntitlementGrantMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 两条闸门 SQL 的迁移，与枚举状态机一条不多、一条不少")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES) {
            String sql = sqlOf(id);
            fromSql.add(statusOf(WHERE_STATUS, sql, id) + "->" + statusOf(SET_STATUS, sql, id));
        }
        Set<String> fromEnum = new HashSet<>();
        for (EntitlementGrantStatusEnum from : EntitlementGrantStatusEnum.values()) {
            for (EntitlementGrantStatusEnum to : EntitlementGrantStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        // 空对空也相等：先确认两边真的有东西
        assertEquals(GATES.size(), fromSql.size(), "两条闸门 SQL 应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql,
                "GradeEntitlementGrantMapper.xml 的闸门与 EntitlementGrantStatusEnum#canTransitTo 对不上");
    }

    @Test
    @DisplayName("每条闸门的两端")
    void 每条闸门的两端() {
        assertEquals(EntitlementGrantStatusEnum.PENDING, statusOf(WHERE_STATUS, sqlOf("tryClaim"), "tryClaim"));
        assertEquals(EntitlementGrantStatusEnum.CLAIMED, statusOf(SET_STATUS, sqlOf("tryClaim"), "tryClaim"));
        assertEquals(EntitlementGrantStatusEnum.PENDING, statusOf(WHERE_STATUS, sqlOf("expirePending"), "expirePending"));
        assertEquals(EntitlementGrantStatusEnum.EXPIRED, statusOf(SET_STATUS, sqlOf("expirePending"), "expirePending"));
    }

    private static EntitlementGrantStatusEnum statusOf(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status 条件。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(EntitlementGrantStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 EntitlementGrantStatusEnum"));
    }

    /** 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，常量拼错或改名会在这里抛异常 */
    private static String sqlOf(String id) {
        String sql = configuration.getMappedStatement(NS + id).getBoundSql(new HashMap<>()).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
