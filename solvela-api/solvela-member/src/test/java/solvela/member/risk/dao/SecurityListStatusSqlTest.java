package solvela.member.risk.dao;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.SecurityListStatusEnum;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台撤销名单的闸门 SQL 与 {@link SecurityListStatusEnum} 对得上。
 *
 * <p>SQL 写在 {@link SecurityListDao} 的注解里，状态值写成 {@code ${@...@X.value}}：常量改名编译器不报错，
 * 只会运行期求值失败 —— 表现是后台撤销一律失败。另外两条边（自动复用、黑到期转灰）是 JdbcTemplate 写的，
 * 状态值绑的是枚举取值，由编译器管。
 */
class SecurityListStatusSqlTest {

    private static Configuration configuration;

    @BeforeAll
    static void parse() {
        configuration = new Configuration();
        configuration.addMapper(SecurityListDao.class);
    }

    @Test
    @DisplayName("🔴 后台撤销：只认有效，改成已撤销，撤销时间用数据库时钟")
    void 撤销只认有效() {
        String sql = configuration.getMappedStatement(SecurityListDao.class.getName() + ".revokeValid")
                .getBoundSql(new HashMap<>()).getSql().replaceAll("\\s+", " ");
        assertFalse(sql.contains("${") || sql.contains("@solvela"), "有没求值的占位：" + sql);
        assertTrue(sql.contains("SET status = " + SecurityListStatusEnum.REVOKED.getValue()), sql);
        assertTrue(sql.contains("AND status = " + SecurityListStatusEnum.VALID.getValue()), "撤销必须带「仍是有效」：" + sql);
        assertTrue(sql.contains("revoke_time = NOW()"), sql);
        assertTrue(SecurityListStatusEnum.VALID.canTransitTo(SecurityListStatusEnum.REVOKED));
    }

    @Test
    @DisplayName("状态机只有有效 ↔ 已撤销两条边")
    void 状态机() {
        int edges = 0;
        for (SecurityListStatusEnum from : SecurityListStatusEnum.values()) {
            for (SecurityListStatusEnum to : SecurityListStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    edges++;
                }
            }
        }
        assertEquals(2, edges, "加了边要在 SecurityListDao / SecurityListWriter / SecurityListMaintainJob 里配上带旧状态条件的 UPDATE");
    }
}
