package solvela.ledger.coupon.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.CouponStatusEnum;
import solvela.ledger.stat.domain.query.LedgerStatQuery;

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
 * 券状态在 SQL 里的每一处，都必须和 {@link CouponStatusEnum} 对得上。
 *
 * <p>状态值写成 {@code ${@solvela.enums.CouponStatusEnum@X.value}}：枚举<b>取值</b>改了 SQL 自动跟着变，
 * 但<b>常量改名</b>时编译器不报错，只会运行期求值失败 —— 表现是锁券、确认、释放全部失败，
 * 商城与充话费两条用券的路同时断掉。
 *
 * <p>所以这里让 MyBatis 真的把 SQL 渲染出来（不连库），断言发给数据库的那一份：
 * 四条闸门（锁定 / 确认 / 释放 / 过期）的迁移与 {@link CouponStatusEnum#canTransitTo} 一条不多、一条不少。
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 * 工具代码刻意各模块自带一份、不抽成共享的 test-jar，理由见 {@code docs/架构/领域实体轻充血-架构设计文档.md}。
 */
class MemberCouponStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("SET\\s+status\\s*=\\s*(\\d+)");

    /** WHERE 之后的第一个 status 条件 —— 闸门的前置状态 */
    private static final Pattern WHERE_STATUS = Pattern.compile("WHERE\\b.*?\\bstatus\\s*=\\s*(\\d+)");

    private static final String NS = MemberCouponDao.class.getName() + ".";

    private static final List<String> GATES =
            List.of("lockCoupon", "confirmCoupon", "releaseCoupon", "expireCouponBatch");

    private static Configuration configuration;

    /** 用裸 {@link Configuration} 而不起 Spring：渲染 SQL 用不到数据源与类型处理器 */
    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/ledger/MemberCouponMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 四条闸门 SQL 的迁移，与枚举状态机一条不多、一条不少")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String method : GATES) {
            String sql = sqlOf(method);
            fromSql.add(edge(statusOf(WHERE_STATUS, sql, method), statusOf(SET_STATUS, sql, method)));
        }

        Set<String> fromEnum = new HashSet<>();
        for (CouponStatusEnum from : CouponStatusEnum.values()) {
            for (CouponStatusEnum to : CouponStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(edge(from, to));
                }
            }
        }

        // 空对空也相等：先确认两边真的有东西，否则状态机被清空时本测试会永远绿着
        assertEquals(GATES.size(), fromSql.size(), "四条闸门 SQL 应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql,
                "MemberCouponMapper.xml 的闸门与 CouponStatusEnum#canTransitTo 对不上。"
                        + "加了新迁移要两边一起加：枚举里加边，XML 里加带旧状态条件的 UPDATE");
    }

    @Test
    @DisplayName("每条闸门的两端：失败时直接指出是哪一条")
    void 每条闸门的两端() {
        assertGate("lockCoupon", CouponStatusEnum.UNUSED, CouponStatusEnum.LOCKED);
        assertGate("confirmCoupon", CouponStatusEnum.LOCKED, CouponStatusEnum.USED);
        assertGate("releaseCoupon", CouponStatusEnum.LOCKED, CouponStatusEnum.UNUSED);
        assertGate("expireCouponBatch", CouponStatusEnum.UNUSED, CouponStatusEnum.EXPIRED);
    }

    @Test
    @DisplayName("按状态捞券的查询：可用候选、过期扫描、到期提醒只看未使用，兜底释放只看锁定中")
    void 查询的状态条件() {
        for (String id : List.of("selectUsableCandidates", "countExpirableCoupon", "selectExpiringGroupByMember")) {
            assertEquals(CouponStatusEnum.UNUSED, statusOf(WHERE_STATUS, sqlOf(id), id), id);
        }
        assertEquals(CouponStatusEnum.LOCKED,
                statusOf(WHERE_STATUS, sqlOf("selectStuckLocked"), "selectStuckLocked"));
    }

    @Test
    @DisplayName("🔴 库存统计给每个状态都留了桶 —— 漏一个，各桶之和就对不上总数")
    void 库存统计每个状态都有桶() {
        String sql = sqlOf("selectStockStat");
        for (CouponStatusEnum s : CouponStatusEnum.values()) {
            assertTrue(sql.contains("SUM(status = " + s.getValue() + ")"),
                    () -> s + " 在库存统计里没有自己的桶（MemberCouponMapper.xml#selectStockStat）："
                            + "管理端的各状态张数加起来会比总数少，而且不报错。SQL: " + sql);
        }
        sqlOf("selectIssuedStat");
        sqlOf("selectCouponStat");
    }

    @Test
    @DisplayName("🔴 闸门 SQL 都不许自己写 update_time —— 那一列归数据库时钟管")
    void 不许手填更新时间() {
        for (String method : GATES) {
            assertFalse(sqlOf(method).toLowerCase().contains("update_time"),
                    method + " 的 SQL 里出现了 update_time，那一列该由数据库自己填（铁律 9）");
        }
    }

    /* ---------------- 工具 ---------------- */

    private static void assertGate(String method, CouponStatusEnum from, CouponStatusEnum to) {
        String sql = sqlOf(method);
        assertEquals(from, statusOf(WHERE_STATUS, sql, method), () -> method + " 的前置状态不对，SQL: " + sql);
        assertEquals(to, statusOf(SET_STATUS, sql, method), () -> method + " 的目标状态不对，SQL: " + sql);
    }

    private static String edge(CouponStatusEnum from, CouponStatusEnum to) {
        return from + "->" + to;
    }

    private static CouponStatusEnum statusOf(Pattern pattern, String sql, String method) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> method + " 的 SQL 里找不到 status 条件。"
                + "改写这条 SQL 时请连同本测试一起改 —— 别把它删掉了事。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(CouponStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        method + " 里的状态值 " + value + " 不是任何一个 CouponStatusEnum：" + sql));
    }

    /** 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，常量拼错或改名会在这里抛异常 */
    private static String sqlOf(String statementId) {
        Map<String, Object> param = new HashMap<>();
        param.put("form", new LedgerStatQuery());
        String sql = configuration.getMappedStatement(NS + statementId).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> statementId + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
