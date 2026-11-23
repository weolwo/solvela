package solvela.lottery.config.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.LotteryConfigStatusEnum;

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
 * 彩票玩法上下线的两条闸门，与 {@link LotteryConfigStatusEnum#canTransitTo} 一条不多、一条不少。
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class LotteryConfigStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_STATUS = Pattern.compile("where\\b.*?\\bstatus\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final List<String> GATES = List.of("markOnline", "markOffline");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/marketing/LotteryConfigMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 上下线两条闸门与枚举状态机一条不多、一条不少")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES) {
            String sql = configuration.getMappedStatement(LotteryConfigDao.class.getName() + "." + id)
                    .getBoundSql(new HashMap<>()).getSql().replaceAll("\\s+", " ");
            assertFalse(sql.contains("${") || sql.contains("@solvela"), id + " 里有没求值的占位：" + sql);
            fromSql.add(statusOf(WHERE_STATUS, sql, id) + "->" + statusOf(SET_STATUS, sql, id));
        }
        Set<String> fromEnum = new HashSet<>();
        for (LotteryConfigStatusEnum from : LotteryConfigStatusEnum.values()) {
            for (LotteryConfigStatusEnum to : LotteryConfigStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertEquals(GATES.size(), fromSql.size(), "两条闸门应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql, "LotteryConfigDao 的闸门与 LotteryConfigStatusEnum#canTransitTo 对不上");
    }

    private static LotteryConfigStatusEnum statusOf(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status 条件。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(LotteryConfigStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 LotteryConfigStatusEnum"));
    }
}
