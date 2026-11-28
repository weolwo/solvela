package solvela.member.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.MemberStatusEnum;

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
 * 后台冻结 / 解冻的两条闸门，与 {@link MemberStatusEnum#canTransitTo} 一条不多、一条不少；已注销不是任何闸门的前置状态。
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class MemberStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_STATUS = Pattern.compile("where\\b.*?\\bstatus\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final List<String> GATES = List.of("markFrozen", "markNormal");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/member/MemberMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 冻结 / 解冻两条闸门与枚举状态机一条不多、一条不少；不写 update_time")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES) {
            String sql = configuration.getMappedStatement(MemberDao.class.getName() + "." + id)
                    .getBoundSql(new HashMap<>()).getSql().replaceAll("\\s+", " ");
            assertFalse(sql.contains("${") || sql.contains("@solvela"), id + " 里有没求值的占位：" + sql);
            assertFalse(sql.contains("update_time"), id + " 不该自己写 update_time：" + sql);
            fromSql.add(statusOf(WHERE_STATUS, sql, id) + "->" + statusOf(SET_STATUS, sql, id));
        }
        Set<String> fromEnum = new HashSet<>();
        for (MemberStatusEnum from : MemberStatusEnum.values()) {
            for (MemberStatusEnum to : MemberStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertEquals(GATES.size(), fromSql.size(), "两条闸门应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql, "MemberDao 的闸门与 MemberStatusEnum#canTransitTo 对不上");
    }

    private static MemberStatusEnum statusOf(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status 条件。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(MemberStatusEnum.values()).filter(s -> s.getValue() == value).findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 MemberStatusEnum"));
    }
}
