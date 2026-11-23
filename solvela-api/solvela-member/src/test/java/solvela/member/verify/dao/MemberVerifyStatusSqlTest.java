package solvela.member.verify.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.MemberVerifyStatusEnum;

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
 * 实名认证审核的两条闸门，与 {@link MemberVerifyStatusEnum#canTransitTo} 一条不多、一条不少。
 *
 * <p>以前审核是先读后 updateById，枚举注释里写着的「并发闸门」并不存在。这里让 MyBatis 渲染出真正发给数据库的 SQL
 * （不连库），断言两条 UPDATE 都带「仍是认证中」、通过时间用数据库时钟。做法与 {@code MallOrderStatusSqlTest} 相同。
 */
class MemberVerifyStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+verify_status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_STATUS = Pattern.compile("where\\b.*?\\bverify_status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final List<String> GATES = List.of("markVerified", "markRejected");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/member/MemberVerifyMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 审核两条闸门与枚举状态机一条不多、一条不少")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES) {
            String sql = sqlOf(id);
            fromSql.add(statusOf(WHERE_STATUS, sql, id) + "->" + statusOf(SET_STATUS, sql, id));
        }
        Set<String> fromEnum = new HashSet<>();
        for (MemberVerifyStatusEnum from : MemberVerifyStatusEnum.values()) {
            for (MemberVerifyStatusEnum to : MemberVerifyStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertEquals(GATES.size(), fromSql.size(), "两条闸门应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql, "MemberVerifyDao 的闸门与 MemberVerifyStatusEnum#canTransitTo 对不上");
    }

    @Test
    @DisplayName("通过：写数据库时钟的通过时间、清掉驳回原因；驳回不写通过时间")
    void 通过与驳回写的列() {
        String verified = sqlOf("markVerified");
        assertTrue(verified.contains("verify_time = NOW()"), "通过时间要用数据库时钟：" + verified);
        assertTrue(verified.contains("fail_reason = NULL"), "通过时要清掉上次的驳回原因：" + verified);
        assertFalse(sqlOf("markRejected").contains("verify_time"), "驳回不该写通过时间");
    }

    private static MemberVerifyStatusEnum statusOf(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 verify_status 条件。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(MemberVerifyStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 MemberVerifyStatusEnum"));
    }

    private static String sqlOf(String id) {
        String sql = configuration.getMappedStatement(MemberVerifyDao.class.getName() + "." + id)
                .getBoundSql(new HashMap<>()).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
