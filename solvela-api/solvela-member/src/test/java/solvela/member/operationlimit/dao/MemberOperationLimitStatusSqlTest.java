package solvela.member.operationlimit.dao;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.MemberOperationLimitStatusEnum;
import solvela.enums.MemberOperationUnlockTypeEnum;

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
 * 操作限制状态在 SQL 里的每一处，都必须和 {@link MemberOperationLimitStatusEnum} 对得上。
 *
 * <p>SQL 写在 {@link MemberOperationLimitDao} 的注解里，状态值写成 {@code ${@...@X.value}}：
 * 常量改名时编译器不报错，只会运行期求值失败 —— 表现是客服点了解冻没反应、到期回写一行都不动。
 * 这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库），断言两条改状态的 UPDATE 与状态机一致、
 * 查生效中只认冻结中、到期回写记的是「自动到期」。做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class MemberOperationLimitStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_STATUS = Pattern.compile("where\\b.*?\\bstatus\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final String NS = MemberOperationLimitDao.class.getName() + ".";

    private static final List<String> GATES = List.of("unlock", "settleExpired");

    private static Configuration configuration;

    /** 注解 SQL：直接把接口注册进裸 Configuration。BaseMapper 那些没有注解的方法会被跳过 */
    @BeforeAll
    static void parse() {
        configuration = new Configuration();
        configuration.addMapper(MemberOperationLimitDao.class);
    }

    @Test
    @DisplayName("🔴 两条改状态的 UPDATE 都是 冻结中 → 已解冻，与枚举状态机一致")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES) {
            String sql = sqlOf(id);
            fromSql.add(statusOf(WHERE_STATUS, sql, id) + "->" + statusOf(SET_STATUS, sql, id));
        }
        Set<String> fromEnum = new HashSet<>();
        for (MemberOperationLimitStatusEnum from : MemberOperationLimitStatusEnum.values()) {
            for (MemberOperationLimitStatusEnum to : MemberOperationLimitStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertFalse(fromEnum.isEmpty(), "canTransitTo 一条边都没有");
        assertEquals(fromEnum, fromSql, "MemberOperationLimitDao 的 UPDATE 与 MemberOperationLimitStatusEnum#canTransitTo 对不上");
    }

    @Test
    @DisplayName("查生效中只认冻结中；到期回写记的是「自动到期」")
    void 查询与解冻方式() {
        assertEquals(MemberOperationLimitStatusEnum.LOCKED, statusOf(WHERE_STATUS, sqlOf("selectActive"), "selectActive"));
        assertTrue(sqlOf("settleExpired").contains("unlock_type = " + MemberOperationUnlockTypeEnum.AUTO_EXPIRE.getValue()),
                sqlOf("settleExpired"));
    }

    @Test
    @DisplayName("🔴 改状态的 UPDATE 不许自己写 update_time —— 那一列归数据库时钟管")
    void 不许手填更新时间() {
        for (String id : GATES) {
            assertFalse(sqlOf(id).toLowerCase().contains("update_time"), id + " 的 SQL 里出现了 update_time");
        }
    }

    private static MemberOperationLimitStatusEnum statusOf(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status 条件。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(MemberOperationLimitStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 MemberOperationLimitStatusEnum"));
    }

    /** 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，常量拼错或改名会在这里抛异常 */
    private static String sqlOf(String id) {
        Map<String, Object> param = new HashMap<>();
        String sql = configuration.getMappedStatement(NS + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
