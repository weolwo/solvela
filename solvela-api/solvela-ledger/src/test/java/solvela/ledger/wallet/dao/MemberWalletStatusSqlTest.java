package solvela.ledger.wallet.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.WalletStatusEnum;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钱包状态在 SQL 里的每一处，都必须和 {@link WalletStatusEnum} 对得上。
 *
 * <p>加减余额的 UPDATE 带「钱包正常」的条件，取值必须与 {@link WalletStatusEnum#allowsBalanceChange} 一致 ——
 * 那是同一条规则在 Java（{@code MemberWallet#checkAvailable}）与 SQL 里的两种实现。状态值写成
 * {@code ${@solvela.enums.WalletStatusEnum@X.value}}，常量改名编译器不报错，只会运行期求值失败 ——
 * 表现是所有加减余额一律失败。这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库）。
 */
class MemberWalletStatusSqlTest {

    private static final Pattern WHERE_STATUS = Pattern.compile("where\\b.*?\\bstatus\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/ledger/MemberWalletMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 加减余额只认 allowsBalanceChange 放行的那个状态")
    void 加减余额只认正常() {
        List<WalletStatusEnum> allowed = Arrays.stream(WalletStatusEnum.values())
                .filter(WalletStatusEnum::allowsBalanceChange).toList();
        assertEquals(1, allowed.size(), "SQL 里写的是 status = 某一个值；放行多个状态时这里要改成 IN 并连同本测试一起改");
        for (String id : List.of("addBalanceWithVersion", "deductBalanceWithVersion")) {
            String sql = sqlOf(id);
            Matcher matcher = WHERE_STATUS.matcher(sql);
            assertTrue(matcher.find(), id + " 必须带钱包状态条件：" + sql);
            assertEquals(allowed.get(0).getValue(), Integer.valueOf(matcher.group(1)), id + " 的状态条件与 allowsBalanceChange 不一致");
            assertTrue(sql.contains("version = ?"), id + " 的乐观锁条件不能丢：" + sql);
        }
        assertTrue(sqlOf("deductBalanceWithVersion").contains("balance >= ?"), "扣减的余额充足条件不能丢");
    }

    @Test
    @DisplayName("统计 SQL 里的状态引用全部求值成功")
    void 统计SQL能渲染() {
        assertTrue(sqlOf("selectStat").contains("status = " + WalletStatusEnum.FROZEN.getValue()));
        assertTrue(sqlOf("selectAssetBalanceStat").contains("status = " + WalletStatusEnum.FROZEN.getValue()));
    }

    private static String sqlOf(String id) {
        String sql = configuration.getMappedStatement(MemberWalletDao.class.getName() + "." + id)
                .getBoundSql(new HashMap<>()).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
