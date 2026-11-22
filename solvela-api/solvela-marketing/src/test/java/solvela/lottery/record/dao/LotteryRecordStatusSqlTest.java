package solvela.lottery.record.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.LotteryDispatchStatusEnum;
import solvela.enums.TicketStatusEnum;
import solvela.lottery.record.domain.query.LotteryRecordQuery;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 彩票记录的中奖状态、派发状态在 SQL 里的每一处，都必须和两个枚举对得上。
 *
 * <p>状态值写成 {@code ${@solvela.enums.XxxEnum@X.value}}：常量改名时编译器不报错，
 * 只会运行期求值失败 —— 表现是开奖认领不到任何一张票、中奖的奖永远派不出去。
 * 这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库），断言开奖、派发的闸门与
 * {@link TicketStatusEnum#canTransitTo}、{@link LotteryDispatchStatusEnum#canTransitTo} 一条不多、一条不少。
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class LotteryRecordStatusSqlTest {

    private static final Pattern SET_WIN = Pattern.compile("SET\\s+win_status\\s*=\\s*(\\d+)");
    private static final Pattern WHERE_WIN = Pattern.compile("WHERE\\b.*?\\bwin_status\\s*=\\s*(\\d+)");
    private static final Pattern SET_DISPATCH = Pattern.compile("SET\\s+dispatch_status\\s*=\\s*(\\d+)");
    private static final Pattern WHERE_DISPATCH = Pattern.compile("WHERE\\b.*?\\bdispatch_status\\s*=\\s*(\\d+)");

    private static final String NS = LotteryRecordDao.class.getName() + ".";

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/marketing/LotteryRecordMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 开奖：认领与判未中奖都只认未开奖，与中奖状态机一条不多、一条不少")
    void 开奖闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : List.of("claimByRule", "markNoWin")) {
            String sql = sqlOf(id);
            fromSql.add(win(WHERE_WIN, sql, id) + "->" + win(SET_WIN, sql, id));
        }
        Set<String> fromEnum = new HashSet<>();
        for (TicketStatusEnum from : TicketStatusEnum.values()) {
            for (TicketStatusEnum to : TicketStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertEquals(2, fromSql.size(), "两条开奖 SQL 应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql, "开奖 SQL 与 TicketStatusEnum#canTransitTo 对不上");
    }

    @Test
    @DisplayName("🔴 派发：标记投递与标记失败都只认待派发，与派发状态机一条不多、一条不少；标失败还要求已中奖")
    void 派发闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : List.of("markDispatched", "markDispatchFailed")) {
            String sql = sqlOf(id);
            fromSql.add(dispatch(WHERE_DISPATCH, sql, id) + "->" + dispatch(SET_DISPATCH, sql, id));
        }
        Set<String> fromEnum = new HashSet<>();
        for (LotteryDispatchStatusEnum from : LotteryDispatchStatusEnum.values()) {
            for (LotteryDispatchStatusEnum to : LotteryDispatchStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertEquals(2, fromSql.size(), "两条派发 SQL 应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql, "派发 SQL 与 LotteryDispatchStatusEnum#canTransitTo 对不上");
        assertEquals(TicketStatusEnum.SUCCESS_MATCH, win(WHERE_WIN, sqlOf("markDispatchFailed"), "markDispatchFailed"),
                "没中奖的票不该被标成投递失败");
    }

    @Test
    @DisplayName("待派发的捞取：已中奖且待派发")
    void 待派发捞取() {
        String sql = sqlOf("selectPendingDispatch");
        assertEquals(TicketStatusEnum.SUCCESS_MATCH, win(WHERE_WIN, sql, "selectPendingDispatch"));
        assertEquals(LotteryDispatchStatusEnum.WAITING, dispatch(WHERE_DISPATCH, sql, "selectPendingDispatch"));
    }

    @Test
    @DisplayName("统计 SQL 里的状态引用全部求值成功")
    void 统计SQL能渲染() {
        for (TicketStatusEnum s : TicketStatusEnum.values()) {
            String sql = sqlOf("selectFunnel");
            assertTrue(sql.contains("SUM(win_status = " + s.getValue() + ")"), () -> s + " 在开奖统计里没有桶：" + sql);
        }
        sqlOf("selectPrizeLevelStat");
    }

    private static TicketStatusEnum win(Pattern p, String sql, String id) {
        return byValue(p, sql, id, v -> {
            for (TicketStatusEnum s : TicketStatusEnum.values()) {
                if (s.getValue() == v) {
                    return s;
                }
            }
            throw new AssertionError(id + " 里的中奖状态值 " + v + " 不是任何一个 TicketStatusEnum");
        });
    }

    private static LotteryDispatchStatusEnum dispatch(Pattern p, String sql, String id) {
        return byValue(p, sql, id, v -> {
            for (LotteryDispatchStatusEnum s : LotteryDispatchStatusEnum.values()) {
                if (s.getValue() == v) {
                    return s;
                }
            }
            throw new AssertionError(id + " 里的派发状态值 " + v + " 不是任何一个 LotteryDispatchStatusEnum");
        });
    }

    private static <E> E byValue(Pattern p, String sql, String id, IntFunction<E> lookup) {
        Matcher m = p.matcher(sql);
        assertTrue(m.find(), () -> id + " 的 SQL 里找不到状态条件（" + p + "）。SQL: " + sql);
        return lookup.apply(Integer.parseInt(m.group(1)));
    }

    /** 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，常量拼错或改名会在这里抛异常 */
    private static String sqlOf(String id) {
        Map<String, Object> param = new HashMap<>();
        param.put("queryForm", new LotteryRecordQuery());
        param.put("matchRule", "EXACT");
        param.put("idList", List.of(1L));
        String sql = configuration.getMappedStatement(NS + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
