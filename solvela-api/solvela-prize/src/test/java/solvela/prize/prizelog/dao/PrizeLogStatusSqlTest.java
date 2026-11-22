package solvela.prize.prizelog.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeProposalStatusEnum;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发奖记录派发状态在 SQL 里的每一处，都必须和 {@link PrizeDispatchStatusEnum} 对得上。
 *
 * <p>状态值写成 {@code ${@solvela.enums.PrizeDispatchStatusEnum@X.value}}：常量改名时编译器不报错，
 * 只会运行期求值失败 —— 表现是发奖结果一条都写不回去，记录永远停在「等待」。
 * 这里让 MyBatis 渲染出真正发给数据库的 SQL（不连库），断言改状态的回写都带「仍是等待」的条件，
 * 迁移都在 {@link PrizeDispatchStatusEnum#canTransitTo} 里，且「已受理」那条根本不碰 status。
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class PrizeLogStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_STATUS = Pattern.compile("where\\b.*?\\bstatus\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final String NS = PrizeLogDao.class.getName() + ".";

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/prize/PrizeLogMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 处理器的两条改状态回写：等待 → 成功 / 失败，且都在状态机里")
    void 处理器回写() {
        assertGate("markDispatchSucceeded", PrizeDispatchStatusEnum.WAITING, PrizeDispatchStatusEnum.SUCCESS);
        assertGate("markDispatchFailed", PrizeDispatchStatusEnum.WAITING, PrizeDispatchStatusEnum.FAIL);
        // 带上提案侧结果的那一种也渲染一遍：<if> 分支里的 SQL 同样要能求值
        Map<String, Object> rejected = new HashMap<>();
        rejected.put("proposalStatus", PrizeProposalStatusEnum.REJECTED);
        assertTrue(sqlOf("markDispatchFailed", rejected).contains("proposal_status"));
    }

    @Test
    @DisplayName("🔴 资产引擎的异步回写只认等待；目标由调用方传，只会是成功或失败")
    void 引擎回写只认等待() {
        assertEquals(PrizeDispatchStatusEnum.WAITING,
                statusOf(WHERE_STATUS, sqlOf("updateStatusByExternalBizNo", new HashMap<>()), "updateStatusByExternalBizNo"));
        // LocalPrizeDispatchResultPublisher 只传 SUCCESS / FAIL 两个值
        assertTrue(PrizeDispatchStatusEnum.WAITING.canTransitTo(PrizeDispatchStatusEnum.SUCCESS));
        assertTrue(PrizeDispatchStatusEnum.WAITING.canTransitTo(PrizeDispatchStatusEnum.FAIL));
    }

    @Test
    @DisplayName("🔴 「已受理」只记提案侧结果，不碰 status —— 终态归资产引擎写")
    void 已受理不碰状态() {
        String sql = sqlOf("markProposalAccepted", new HashMap<>());
        assertFalse(SET_STATUS.matcher(sql).find(), "markProposalAccepted 不该改 status：" + sql);
        assertTrue(sql.contains("proposal_status = " + PrizeProposalStatusEnum.ACCEPTED.getValue()), sql);
    }

    private static void assertGate(String id, PrizeDispatchStatusEnum from, PrizeDispatchStatusEnum to) {
        String sql = sqlOf(id, new HashMap<>());
        assertEquals(from, statusOf(WHERE_STATUS, sql, id), () -> id + " 的前置状态不对，SQL: " + sql);
        assertEquals(to, statusOf(SET_STATUS, sql, id), () -> id + " 的目标状态不对，SQL: " + sql);
        assertTrue(from.canTransitTo(to), id + " 的迁移不在状态机里");
    }

    private static PrizeDispatchStatusEnum statusOf(Pattern pattern, String sql, String id) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> id + " 的 SQL 里找不到 status 条件。SQL: " + sql);
        int value = Integer.parseInt(matcher.group(1));
        return Arrays.stream(PrizeDispatchStatusEnum.values())
                .filter(s -> s.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " 里的状态值 " + value + " 不是任何一个 PrizeDispatchStatusEnum"));
    }

    /** 让 MyBatis 渲染出真正会发给数据库的 SQL。{@code ${}} 在这一步求值，常量拼错或改名会在这里抛异常 */
    private static String sqlOf(String id, Map<String, Object> param) {
        String sql = configuration.getMappedStatement(NS + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
