package solvela.admin.enums;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 枚举映射对账用的<b>自带数据</b>。
 *
 * <h3>🔴 为什么 2026-09-24 才需要它</h3>
 * 这一批测试（2026-08-29 写的）原本靠<b>开发库里碰巧有什么</b>：
 * 类注释里写着「抽奖流水有 500 行」「库里 approve_status 是 0×1230 / 1×66 / 3×1」。
 * 那是枚举改造时的<b>存量数据对账</b> —— 一次性的，结论已经落在
 * {@code docs/枚举改造-存量数据对账报告.md} 里。
 *
 * <p>但那批数据全部挂在压测/验收会员（{@code stress_} / {@code p0_}）名下。
 * 2026-09-24 把它们清掉（2301 个会员、约 1.3 万行）之后，6 条断言当场变红。
 *
 * <p>⚠️ 更要紧的是：<b>它们在一个全新环境上本来就跑不过</b>。
 * {@code VerifyFreshInstall} 建出来的库没有这些行，
 * 也就是说这几个用例一直在「只有我这台机器能过」的状态，只是没人验过。
 *
 * <h3>做法：写原始 int，读走 DAO，断言枚举</h3>
 * 这里用 {@link JdbcTemplate} 直接写<b>数字</b>（{@code -1} / {@code 2} …），
 * 而不是用实体 + 枚举去写 —— 用枚举写再用枚举读，等于拿同一个 handler
 * 自己验自己，反过来也成立，那种用例永远是绿的。
 *
 * <p>写进去的是「库里长什么样」，读出来的是「代码认成了什么」，
 * 这才是这批用例一开始要对的账。
 *
 * <h3>⚠️ 调用方必须带 {@code @Transactional}</h3>
 * 这些行只在用例执行期间存在，方法结束由 Spring 回滚。
 * 不回滚的话，这个类就变成了它要解决的那个问题本身 ——
 * 又一批「留在开发库里、没人知道谁造的」数据。
 *
 * @author alaric
 * @date 2026-09-24
 */
final class EnumMappingFixture {

    /**
     * 夹具会员号。刻意用一个<b>不存在于 t_member 的</b>号：
     * 这些表都只存 member_id 软引用，不建外键（见各自的 DDL 注释），
     * 所以不需要先造会员。用 9 开头是为了和真实会员号（10 位随机）区分开，
     * 万一哪天回滚失败，一眼能认出是谁留下的。
     */
    static final long MEMBER_ID = 9_000_000_001L;

    private EnumMappingFixture() {
    }

    /** 履约单：必须有一条 {@code status = -1}，那正是「负值能不能装配成枚举」要问的 */
    static void seedPhysicalDelivery(JdbcTemplate jdbc) {
        insertDelivery(jdbc, "FIXD-CANCELLED", -1);
        insertDelivery(jdbc, "FIXD-PENDING", 0);
        insertDelivery(jdbc, "FIXD-SIGNED", 2);
    }

    private static void insertDelivery(JdbcTemplate jdbc, String bizId, int status) {
        jdbc.update("INSERT INTO t_physical_delivery (member_id, source_type, source_biz_id, status) "
                + "VALUES (?, 'FIXTURE', ?, ?)", MEMBER_ID, bizId, status);
    }

    /**
     * 抽奖流水：已中奖要比库存不足多。
     *
     * <p>⚠️ 比例是<b>有意义的</b>，不是凑数：库存不足反过来比中奖多，说明奖池配少了，
     * 而原用例正是拿这个不等式当「取值口径有没有反」的信号。
     */
    static void seedDrawPrizeLog(JdbcTemplate jdbc) {
        insertDraw(jdbc, "FIXT-HIT-1", 1);
        insertDraw(jdbc, "FIXT-HIT-2", 1);
        insertDraw(jdbc, "FIXT-HIT-3", 1);
        insertDraw(jdbc, "FIXT-NOSTOCK-1", 2);
        insertDraw(jdbc, "FIXT-MISS-1", 0);
    }

    private static void insertDraw(JdbcTemplate jdbc, String traceId, int status) {
        jdbc.update("INSERT INTO t_draw_prize_log "
                        + "(member_id, trace_id, activity_code, pool_code, prize_item_id, prize_code, status) "
                        + "VALUES (?, ?, 'FIXACT', 'FIXPOOL', 1, 'FIXPRIZE', ?)",
                MEMBER_ID, traceId, status);
    }

    /**
     * 发奖记录：审批与执行是<b>两个维度</b>，各自独立。
     *
     * <p>「无需审批」要多于「待审批」（绝大多数奖品配的是自动免审），
     * 「发放成功」要多于「失败」—— 两条不等式都是原用例用来发现「0/1 口径反了」的。
     * 另外留一条「已批准但发放失败」，那是合法组合，用例专门验它能并存。
     */
    static void seedPrizeLog(JdbcTemplate jdbc) {
        // approve_status, status(dispatch)
        insertPrize(jdbc, "FIXP-1", 0, 1);
        insertPrize(jdbc, "FIXP-2", 0, 1);
        insertPrize(jdbc, "FIXP-3", 0, 1);
        insertPrize(jdbc, "FIXP-4", 1, 0);
        // 已批准(2) + 发放失败(2)：两个维度并存的那条
        insertPrize(jdbc, "FIXP-5", 2, 2);
    }

    private static void insertPrize(JdbcTemplate jdbc, String prizeCode, int approveStatus, int dispatchStatus) {
        jdbc.update("INSERT INTO t_prize_log "
                        + "(member_id, prize_code, activity_code, prize_name, prize_type, prize_value, "
                        + " approve_status, status) "
                        + "VALUES (?, ?, 'FIXACT', '夹具奖品', 'SCORE', '1', ?, ?)",
                MEMBER_ID, prizeCode, approveStatus, dispatchStatus);
    }

    /**
     * 任务记录：至少一条达标（{@code status >= COMPLETED(1)}）。
     *
     * <p>⚠️ 三条必须用<b>不同的 task_config_id</b>：
     * {@code uk(member_id, task_config_id, period_key)} 挡着 ——
     * 一个人对同一个任务在同一周期只能有一条记录，那正是这张表的防重设计。
     */
    static void seedTaskRecord(JdbcTemplate jdbc) {
        insertTaskRecord(jdbc, 901, 0);
        insertTaskRecord(jdbc, 902, 1);
        insertTaskRecord(jdbc, 903, 2);
    }

    private static void insertTaskRecord(JdbcTemplate jdbc, long taskConfigId, int status) {
        jdbc.update("INSERT INTO t_task_record "
                        + "(member_id, task_config_id, activity_code, valid_start_time, valid_end_time, "
                        + " rule_snapshot, prize_snapshot, status) "
                        + "VALUES (?, ?, 'FIXACT', '2026-01-01 00:00:00', '2099-12-31 23:59:59', '{}', '{}', ?)",
                MEMBER_ID, taskConfigId, status);
    }

    /**
     * 任务流水：推进要远多于丢弃。
     *
     * <p>⚠️ 只造这两种类型，因为原用例还断言「分类型计数之和 == 总量」——
     * 多造一种没被计数的类型，那条会红，而红的原因和它要守的东西无关。
     */
    static void seedTaskRecordFlow(JdbcTemplate jdbc) {
        insertFlow(jdbc, "FIXF-A1", 1);
        insertFlow(jdbc, "FIXF-A2", 1);
        insertFlow(jdbc, "FIXF-A3", 1);
        insertFlow(jdbc, "FIXF-D1", 2);
    }

    private static void insertFlow(JdbcTemplate jdbc, String eventBizId, int flowType) {
        jdbc.update("INSERT INTO t_task_record_flow "
                        + "(member_id, task_config_id, event_code, event_biz_id, flow_type) "
                        + "VALUES (?, 1, 'FIXEVENT', ?, ?)",
                MEMBER_ID, eventBizId, flowType);
    }
}
