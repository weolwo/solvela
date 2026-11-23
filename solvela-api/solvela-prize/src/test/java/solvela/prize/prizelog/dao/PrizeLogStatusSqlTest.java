package solvela.prize.prizelog.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.BaseEnum;
import solvela.enums.PrizeApproveStatusEnum;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeProposalStatusEnum;

import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    @DisplayName("🔴 处理器的三条改派发状态回写：等待 → 成功 / 失败，且都在状态机里")
    void 处理器回写() {
        assertGate("markDispatchSucceeded", PrizeDispatchStatusEnum.WAITING, PrizeDispatchStatusEnum.SUCCESS);
        assertGate("markDispatchFailed", PrizeDispatchStatusEnum.WAITING, PrizeDispatchStatusEnum.FAIL);
        assertGate("markProposalRejected", PrizeDispatchStatusEnum.WAITING, PrizeDispatchStatusEnum.FAIL);
        // 执行异常那条还不知道提案结果，不该动提案列
        assertFalse(sqlOf("markDispatchFailed", new HashMap<>()).contains("proposal_status"));
    }

    @Test
    @DisplayName("🔴 审批列的闸门与 PrizeApproveStatusEnum 状态机一条不多、一条不少")
    void 审批闸门与状态机一一对应() {
        assertColumnGates("approve_status", PrizeApproveStatusEnum.class, PrizeApproveStatusEnum::canTransitTo, Map.of(
                "markApproved", List.of(PrizeApproveStatusEnum.PENDING, PrizeApproveStatusEnum.PASSED),
                "markApprovalRejected", List.of(PrizeApproveStatusEnum.PENDING, PrizeApproveStatusEnum.REJECTED)));
    }

    @Test
    @DisplayName("🔴 提案列的闸门与 PrizeProposalStatusEnum 状态机一条不多、一条不少")
    void 提案闸门与状态机一一对应() {
        assertColumnGates("proposal_status", PrizeProposalStatusEnum.class, PrizeProposalStatusEnum::canTransitTo, Map.of(
                "markProposalAccepted", List.of(PrizeProposalStatusEnum.PENDING, PrizeProposalStatusEnum.ACCEPTED),
                "markProposalRejected", List.of(PrizeProposalStatusEnum.PENDING, PrizeProposalStatusEnum.REJECTED)));
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

    @Test
    @DisplayName("🔴 对账重投只捞：提案待提交、仍在等待、审批允许派发（与 allowsDispatch 同一口径）")
    void 对账重投的取数口径() {
        String sql = sqlOf("selectRetryable", new HashMap<>());
        assertTrue(sql.contains("proposal_status = " + PrizeProposalStatusEnum.PENDING.getValue()), sql);
        assertEquals(PrizeDispatchStatusEnum.WAITING, statusOf(WHERE_STATUS, sql, "selectRetryable"));

        Matcher matcher = Pattern.compile("approve_status\\s+in\\s*\\(([\\d,\\s]+)\\)", Pattern.CASE_INSENSITIVE).matcher(sql);
        assertTrue(matcher.find(), "selectRetryable 里找不到 approve_status in (...)：" + sql);
        Set<PrizeApproveStatusEnum> fromSql = Arrays.stream(matcher.group(1).split(","))
                .map(v -> Integer.parseInt(v.trim()))
                .map(v -> Arrays.stream(PrizeApproveStatusEnum.values()).filter(s -> s.getValue() == v).findFirst()
                        .orElseThrow(() -> new AssertionError("审批状态值 " + v + " 不是任何一个 PrizeApproveStatusEnum")))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PrizeApproveStatusEnum.class)));
        Set<PrizeApproveStatusEnum> fromEnum = Arrays.stream(PrizeApproveStatusEnum.values())
                .filter(PrizeApproveStatusEnum::allowsDispatch)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PrizeApproveStatusEnum.class)));
        // 空对空也相等：口径被改成「谁都不能发」时本测试不该绿着
        assertFalse(fromEnum.isEmpty(), "allowsDispatch 对所有审批状态都为假");
        assertFalse(fromEnum.contains(PrizeApproveStatusEnum.PENDING) || fromEnum.contains(PrizeApproveStatusEnum.REJECTED),
                "待审批、已驳回的奖不能派发");
        assertEquals(fromEnum, fromSql, "selectRetryable 的审批状态与 PrizeApproveStatusEnum#allowsDispatch 对不上");
    }

    /**
     * 某一列的闸门：每条 SQL 的「SET 列 = 目标」与「WHERE 列 = 前置」两端对得上 {@code expected}，
     * 且这些边与枚举的 canTransitTo 一条不多、一条不少。
     */
    private static <E extends Enum<E> & BaseEnum> void assertColumnGates(
            String column, Class<E> type, BiPredicate<E, E> canTransit, Map<String, List<E>> expected) {
        Pattern value = Pattern.compile("(?<![\\w])" + column + "\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Set<String> fromSql = new HashSet<>();
        expected.forEach((id, ends) -> {
            String sql = sqlOf(id, new HashMap<>());
            int where = sql.toLowerCase().indexOf(" where ");
            assertTrue(where > 0, id + " 没有 WHERE：" + sql);
            E from = enumOf(type, value, sql.substring(where), id + " 的前置 " + column);
            E to = enumOf(type, value, sql.substring(0, where), id + " 的目标 " + column);
            assertEquals(ends.get(0), from, () -> id + " 的前置状态不对，SQL: " + sql);
            assertEquals(ends.get(1), to, () -> id + " 的目标状态不对，SQL: " + sql);
            fromSql.add(from + "->" + to);
        });
        Set<String> fromEnum = new HashSet<>();
        for (E from : type.getEnumConstants()) {
            for (E to : type.getEnumConstants()) {
                if (canTransit.test(from, to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        // 空对空也相等：先确认真的有边
        assertEquals(expected.size(), fromSql.size(), column + " 的闸门应当各是一条不同的迁移：" + fromSql);
        assertEquals(fromEnum, fromSql, column + " 的闸门与 " + type.getSimpleName() + "#canTransitTo 对不上。"
                + "加了新迁移要两边一起加：枚举里加边，DAO 里加带旧状态条件的 UPDATE");
    }

    private static <E extends Enum<E> & BaseEnum> E enumOf(Class<E> type, Pattern pattern, String sql, String what) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), () -> what + " 找不到。SQL 片段: " + sql);
        int v = Integer.parseInt(matcher.group(1));
        return Arrays.stream(type.getEnumConstants()).filter(e -> Integer.valueOf(v).equals(e.getValue())).findFirst()
                .orElseThrow(() -> new AssertionError(what + " 的值 " + v + " 不是任何一个 " + type.getSimpleName()));
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
