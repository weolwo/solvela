package solvela.activity.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.ActivityStatusEnum;

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
 * 活动状态在 SQL 里的每一处，都必须和 {@link ActivityStatusEnum} 对得上。
 *
 * <p>状态值写成 {@code ${@solvela.enums.ActivityStatusEnum@X.value}}：常量改名时编译器不报错，只会运行期求值失败 ——
 * 表现是上下线点了没反应、删除一个都删不掉。这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库），断言上下线两条闸门
 * 与 {@link ActivityStatusEnum#canTransitTo} 一条不多、一条不少，且删除只认未开始。
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class ActivityConfigStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    /** {@code status = 0} 或 {@code status in (0, 2)} */
    private static final Pattern WHERE_STATUS = Pattern.compile(
            "where\\b.*?\\bstatus\\s*(?:=\\s*(\\d+)|in\\s*\\(([\\d,\\s]+)\\))", Pattern.CASE_INSENSITIVE);

    private static final String NS = ActivityConfigDao.class.getName() + ".";

    private static final List<String> GATES = List.of("markOnline", "markOffline");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/activity/ActivityConfigMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 上下线两条闸门的迁移，与枚举状态机一条不多、一条不少")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES) {
            String sql = sqlOf(id);
            ActivityStatusEnum to = single(SET_STATUS, sql, id);
            fromOf(sql, id).forEach(from -> fromSql.add(from + "->" + to));
        }
        Set<String> fromEnum = new HashSet<>();
        for (ActivityStatusEnum from : ActivityStatusEnum.values()) {
            for (ActivityStatusEnum to : ActivityStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertFalse(fromEnum.isEmpty(), "canTransitTo 一条边都没有");
        assertEquals(fromEnum, fromSql, "ActivityConfigDao 的闸门与 ActivityStatusEnum#canTransitTo 对不上。"
                + "加了新迁移要两边一起加：枚举里加边，DAO 里加带旧状态条件的 UPDATE");
        for (ActivityStatusEnum from : ActivityStatusEnum.values()) {
            assertFalse(from.canTransitTo(ActivityStatusEnum.NOT_START), "离开未开始就回不去，否则上过线的活动又能被删");
        }
    }

    @Test
    @DisplayName("🔴 删除（单个、批量）只认未开始")
    void 删除只认未开始() {
        for (String id : List.of("deleteNotStarted", "deleteNotStartedBatch")) {
            assertEquals(EnumSet.of(ActivityStatusEnum.NOT_START), fromOf(sqlOf(id), id), id + " 必须只删未开始的活动");
        }
    }

    private static Set<ActivityStatusEnum> fromOf(String sql, String id) {
        Matcher matcher = WHERE_STATUS.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status 条件。SQL: " + sql);
        String values = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return Arrays.stream(values.split(","))
                .map(v -> byValue(Integer.parseInt(v.trim()), id))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ActivityStatusEnum.class)));
    }

    private static ActivityStatusEnum single(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 set status。SQL: " + sql);
        return byValue(Integer.parseInt(matcher.group(1)), id);
    }

    private static ActivityStatusEnum byValue(int value, String id) {
        return Arrays.stream(ActivityStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 ActivityStatusEnum"));
    }

    /** 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，常量拼错或改名会在这里抛异常 */
    private static String sqlOf(String id) {
        Map<String, Object> param = new HashMap<>();
        param.put("idList", List.of(1L, 2L));
        String sql = configuration.getMappedStatement(NS + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
