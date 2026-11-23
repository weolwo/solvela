package solvela.ledger.logistic.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.DeliveryStatusEnum;

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
 * 履约单状态在 SQL 里的每一处，都必须和 {@link DeliveryStatusEnum} 对得上。
 *
 * <p>状态值写成 {@code ${@solvela.enums.DeliveryStatusEnum@X.value}}：常量改名时编译器不报错，
 * 只会运行期求值失败 —— 表现是回填导入一条都写不进去。这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库），断言：
 * <ul>
 *   <li>改状态的闸门与 {@link DeliveryStatusEnum#canTransitTo} 的边一条不多、一条不少；</li>
 *   <li>只补物流信息的那条不碰状态；</li>
 *   <li>统计 SQL 里的状态引用全部求值成功。</li>
 * </ul>
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class PhysicalDeliveryStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+status\\s*=\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);

    /** {@code and status = 0} 或 {@code and status in (0, 1)}：闸门的前置状态 */
    private static final Pattern WHERE_STATUS = Pattern.compile(
            "where\\b.*?\\bstatus\\s*(?:=\\s*(-?\\d+)|in\\s*\\(([-\\d,\\s]+)\\))", Pattern.CASE_INSENSITIVE);

    private static final String NS = PhysicalDeliveryDao.class.getName() + ".";

    /** 闸门 → [目标, 前置...] */
    private static final Map<String, List<DeliveryStatusEnum>> GATES = Map.of(
            "discardById", List.of(DeliveryStatusEnum.CANCELLED, DeliveryStatusEnum.PENDING),
            "discardBatchIds", List.of(DeliveryStatusEnum.CANCELLED, DeliveryStatusEnum.PENDING),
            "markShipped", List.of(DeliveryStatusEnum.DELIVERED, DeliveryStatusEnum.PENDING),
            "markSigned", List.of(DeliveryStatusEnum.SIGNED, DeliveryStatusEnum.PENDING, DeliveryStatusEnum.DELIVERED),
            "markReturned", List.of(DeliveryStatusEnum.RETURNED, DeliveryStatusEnum.DELIVERED));

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/ledger/PhysicalDeliveryMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 闸门 SQL 的迁移，与枚举状态机一条不多、一条不少")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        GATES.forEach((id, expected) -> {
            String sql = sqlOf(id);
            DeliveryStatusEnum to = byValue(group(SET_STATUS, sql, id).get(0), id);
            Set<DeliveryStatusEnum> from = fromOf(sql, id);
            assertEquals(expected.get(0), to, () -> id + " 的目标状态不对，SQL: " + sql);
            assertEquals(EnumSet.copyOf(expected.subList(1, expected.size())), from, () -> id + " 的前置状态不对，SQL: " + sql);
            from.forEach(f -> fromSql.add(f + "->" + to));
        });

        Set<String> fromEnum = new HashSet<>();
        for (DeliveryStatusEnum from : DeliveryStatusEnum.values()) {
            for (DeliveryStatusEnum to : DeliveryStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        // 空对空也相等：先确认真的有边（作废的单个、批量是同一条边，所以边数比闸门数少 1）
        assertEquals(GATES.size(), fromSql.size(), "闸门渲染出的迁移数不对：" + fromSql);
        assertEquals(fromEnum, fromSql, "PhysicalDeliveryDao 的闸门与 DeliveryStatusEnum#canTransitTo 对不上。"
                + "加了新迁移要两边一起加：枚举里加边，DAO 里加带旧状态条件的 UPDATE");
    }

    @Test
    @DisplayName("只补物流信息的那条不碰状态，物流两列空值不覆盖")
    void 补物流不碰状态() {
        String sql = sqlOf("updateLogistics");
        assertFalse(SET_STATUS.matcher(sql).find() || sql.contains(" status"), "updateLogistics 不该碰 status：" + sql);
        for (String id : List.of("updateLogistics", "markShipped", "markSigned", "markReturned")) {
            String gate = sqlOf(id);
            assertTrue(gate.contains("COALESCE(?, logistics_company)") && gate.contains("COALESCE(?, logistics_no)"),
                    id + " 的物流两列应当空值不覆盖：" + gate);
        }
    }

    @Test
    @DisplayName("🔴 闸门 SQL 都不许自己写 update_time —— 那一列归数据库时钟管")
    void 不许手填更新时间() {
        for (String id : GATES.keySet()) {
            assertFalse(sqlOf(id).toLowerCase().contains("update_time"), id + " 的 SQL 里出现了 update_time");
        }
    }

    @Test
    @DisplayName("统计 SQL 里的状态引用全部求值成功")
    void 统计SQL能渲染() {
        String sql = sqlOf("selectStatusStat");
        assertTrue(sql.contains("status = " + DeliveryStatusEnum.CANCELLED.getValue()), sql);
        assertTrue(sqlOf("selectSourceStat").contains("status = " + DeliveryStatusEnum.PENDING.getValue()));
    }

    /* ---------------- 工具 ---------------- */

    private static Set<DeliveryStatusEnum> fromOf(String sql, String id) {
        Matcher matcher = WHERE_STATUS.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到前置状态条件。改写这条 SQL 时请连同本测试一起改。SQL: " + sql);
        String values = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return Arrays.stream(values.split(","))
                .map(v -> byValue(Integer.parseInt(v.trim()), id))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DeliveryStatusEnum.class)));
    }

    private static List<Integer> group(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status。SQL: " + sql);
        return List.of(Integer.parseInt(matcher.group(1)));
    }

    private static DeliveryStatusEnum byValue(int value, String id) {
        return Arrays.stream(DeliveryStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 DeliveryStatusEnum"));
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
