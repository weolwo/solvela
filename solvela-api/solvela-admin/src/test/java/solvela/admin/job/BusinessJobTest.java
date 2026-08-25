package solvela.admin.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.risk.proposal.dao.ProposalRecordDao;
import solvela.task.record.dao.TaskRecordDao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三个业务定时任务的取数与收口 SQL 验收（真跑数据库）。
 *
 * <h3>为什么这三条 SQL 特别需要被测</h3>
 * 它们平时<b>没有人会点到</b>：收口任务半夜跑、跑完只在日志里留一行。
 * 写错了不会有人当场发现 —— 最好的情况是"什么都没收口"（安静地不干活），
 * 最坏的情况是条件写漏把不该改的行也改了，<b>而且不可逆</b>。
 *
 * <p>所以这里做两件事：
 * <ol>
 *   <li><b>SQL 真的能跑</b>：{@code UPDATE ... LIMIT} 这种写法只有执行时才会暴露语法问题，
 *       编译期和启动期都看不出来；</li>
 *   <li><b>改动范围与统计口径一致</b>：收口影响的行数必须正好等于"待收口"的条数，
 *       多一行都说明条件写宽了。</li>
 * </ol>
 *
 * <p>⚠️ 收口类用例<b>自己造数</b>，不依赖开发库里恰好有过期数据：
 * 实测跑第一遍时库里待收口的券和任务记录都是 0 条 —— 那种情况下断言全部「0 == 0」通过，
 * 而它证明不了任何事（铁律 16：前提不成立时，通过和空过分不出来）。
 * 造的数连同 UPDATE 一起在事务里回滚。
 *
 * <p>⚠️ 涉及 UPDATE 的用例标了 {@link Transactional} + {@link Rollback}：
 * 语句真的执行、真的能验证影响行数，但测试结束回滚，<b>不会把开发库的数据改掉</b>。
 * 收口本身是不可逆的（券一旦标成已过期就没有回头路），
 * 一个测试不该有能力顺手把库里的数据收口掉。
 *
 * @author alaric
 * @date 2026-08-18
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class BusinessJobTest {

    @Autowired
    private MemberCouponDao memberCouponDao;
    @Autowired
    private TaskRecordDao taskRecordDao;
    @Autowired
    private ProposalRecordDao proposalRecordDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 用数据库时钟，不用 JVM 的 —— 任务里传进去的就是它（铁律 9）
     */
    private LocalDateTime dbNow() {
        LocalDateTime now = jdbcTemplate.queryForObject("SELECT now()", LocalDateTime.class);
        assertNotNull(now);
        return now;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    // ==================== 优惠券过期收口 ====================

    @Test
    @DisplayName("券收口：待收口条数与库里一致（试运行读的就是这个数）")
    void couponExpirableCountMatchesDb() {
        LocalDateTime now = dbNow();
        long expected = count("SELECT COUNT(*) FROM t_member_coupon"
                + " WHERE status = 0 AND valid_end_time < now()");
        assertEquals(expected, memberCouponDao.countExpirableCoupon(now),
                "试运行报出来的张数与库里对不上");
    }

    /**
     * 造一张券。{@code minutesFromNow} 为负表示有效期已经过了。
     *
     * <p>只填 NOT NULL 的列，其余走默认值 —— 这里要证明的是 WHERE 条件的边界，
     * 不是券本身长什么样。
     */
    /**
     * 造数用的会员号：由账号推一个稳定的 10 位数，<b>不落 t_member</b>。
     *
     * <p>本文件验的是两个收口任务的 WHERE 边界，SQL 里不 join 会员表，
     * 所以不需要真实会员；不造会员也就不会在库里留下一堆测试会员。
     * 需要真实会员的用例（走 report 入口那种）见 TaskRuntimeP0AcceptanceTest。
     */
    private long fakeMemberId(String memberName) {
        return 1_000_000_000L + Math.floorMod(memberName.hashCode(), 8_000_000_000L);
    }

    private void insertCoupon(String memberName, int status, int minutesFromNow) {
        jdbcTemplate.update(
                // member_id 是 NOT NULL 的关联键（v3.71.0）：漏了会以
                // 「Field 'member_id' doesn't have a default value」整条被拒。
                // 这里只造券、不造会员：本用例验的是收口 SQL 的 WHERE 边界，不 join 会员表
                "INSERT INTO t_member_coupon (member_id, member_name, coupon_code, coupon_type,"
                        + " coupon_name, status, source_type, source_biz_id, valid_start_time, valid_end_time)"
                        + " VALUES (?, ?, 'JOB_TEST', 'CASH', '收口用例造的券', ?, 'TEST', ?,"
                        + " DATE_SUB(now(), INTERVAL 1 DAY), DATE_ADD(now(), INTERVAL ? MINUTE))",
                fakeMemberId(memberName), memberName, status,
                memberName + "-" + System.nanoTime(), minutesFromNow);
    }

    @Test
    @Transactional
    @Rollback
    @DisplayName("券收口：只动「未使用且已过期」的行，一行不多（自己造数，跑完回滚）")
    void couponExpireOnlyTouchesExpirable() {
        LocalDateTime now = dbNow();
        /*
         * ⚠️ 基线必须用 jdbcTemplate 读，不能在造数前先调一次 DAO ——
         * 事务内 MyBatis 复用同一个 SqlSession，一级缓存会把「造数前」的结果原样返回，
         * 而 jdbcTemplate 的 INSERT 不经过 MyBatis，清不掉那份缓存。
         * 第一版就是这么写的：造了 3 张券，DAO 仍然回答 0，看上去像 SQL 写错了。
         */
        long expirableBefore = count("SELECT COUNT(*) FROM t_member_coupon"
                + " WHERE status = 0 AND valid_end_time < now()");

        // 三张该收口的
        insertCoupon("jobTestA", 0, -60);
        insertCoupon("jobTestB", 0, -60);
        insertCoupon("jobTestC", 0, -60);
        // 一张还没到期的：不该被碰
        insertCoupon("jobTestFresh", 0, 60);
        // 一张已使用但也过期了的：同样不该被碰（它的终态已经是「用掉了」）
        insertCoupon("jobTestUsed", 1, -60);

        long totalBefore = count("SELECT COUNT(*) FROM t_member_coupon");
        assertEquals(expirableBefore + 3, memberCouponDao.countExpirableCoupon(now),
                "待收口条数没有把新造的三张算进去");

        int rows = memberCouponDao.expireCouponBatch(now, 100000);
        assertEquals(expirableBefore + 3, rows,
                "收口影响的行数与「待收口」对不上：多一行都说明 WHERE 条件写宽了，"
                        + "而这个操作是不可逆的");

        // 收口只改状态，不该删掉或新增任何行
        assertEquals(totalBefore, count("SELECT COUNT(*) FROM t_member_coupon"));
        assertEquals(0L, memberCouponDao.countExpirableCoupon(now), "收口之后不该还有待收口的券");
        assertEquals(0, count("SELECT COUNT(*) FROM t_member_coupon"
                        + " WHERE member_name = 'jobTestFresh' AND status <> 0"),
                "还没到期的券被收口了");
        assertEquals(1, count("SELECT COUNT(*) FROM t_member_coupon"
                        + " WHERE member_name = 'jobTestUsed' AND status = 1"),
                "已使用的券被改成了已过期 —— 那是在篡改一张用户已经用掉的券的终态");

        // 幂等：再跑一次影响行数必须是 0
        assertEquals(0, memberCouponDao.expireCouponBatch(now, 100000),
                "第二次执行还在改行，说明这条 SQL 不幂等，配了失败重试就会重复副作用");
    }

    @Test
    @Transactional
    @Rollback
    @DisplayName("券收口：LIMIT 真的生效，一次最多收 N 条")
    void couponExpireRespectsBatchLimit() {
        LocalDateTime now = dbNow();
        insertCoupon("jobTestLimit1", 0, -60);
        insertCoupon("jobTestLimit2", 0, -60);
        assertTrue(count("SELECT COUNT(*) FROM t_member_coupon"
                + " WHERE status = 0 AND valid_end_time < now()") >= 2, "造数没生效");

        assertEquals(1, memberCouponDao.expireCouponBatch(now, 1),
                "LIMIT 没生效：分批是为了不长时间持锁，失效了就等于一次锁全表");
    }

    // ==================== 任务记录过期收口 ====================

    @Test
    @DisplayName("任务收口：待收口条数与库里一致")
    void taskExpirableCountMatchesDb() {
        LocalDateTime now = dbNow();
        long expected = count("SELECT COUNT(*) FROM t_task_record"
                + " WHERE status = 0 AND valid_end_time < now()");
        assertEquals(expected, taskRecordDao.countExpirableRecord(now),
                "试运行报出来的条数与库里对不上");
    }

    /**
     * 造一条任务记录。{@code minutesFromNow} 为负表示有效期已经过了。
     */
    private void insertTaskRecord(String memberName, int status, int minutesFromNow) {
        jdbcTemplate.update(
                // 🔴 刻意<b>不写</b> member_name：任务记录是状态表，那一列已被 v3.71.1 放开为可空、
                // 由 v3.72.0 删除，代码侧也不再写它。这里跟着不写，本用例才能跨过那次删列继续跑。
                "INSERT INTO t_task_record (member_id, task_config_id, activity_code,"
                        + " period_key, valid_start_time, valid_end_time, status, rule_snapshot, prize_snapshot)"
                        + " VALUES (?, 0, 'JOB_TEST', ?, DATE_SUB(now(), INTERVAL 1 DAY),"
                        + " DATE_ADD(now(), INTERVAL ? MINUTE), ?, '{}', '[]')",
                fakeMemberId(memberName),
                memberName + "-" + System.nanoTime(), minutesFromNow, status);
    }

    @Test
    @Transactional
    @Rollback
    @DisplayName("任务收口：不误伤已完成/已发奖的记录（自己造数，跑完回滚）")
    void taskExpireDoesNotTouchFinishedRecord() {
        LocalDateTime now = dbNow();
        // 基线走 jdbcTemplate：造数前调 DAO 会被 MyBatis 一级缓存挡住（见券那个用例的注释）
        long expirableBefore = count("SELECT COUNT(*) FROM t_task_record"
                + " WHERE status = 0 AND valid_end_time < now()");

        insertTaskRecord("jobTaskA", 0, -60);
        insertTaskRecord("jobTaskB", 0, -60);
        // 已完成、已发奖：即使有效期过了也不该被动 —— 用户已经达标，动它等于把奖吞掉
        insertTaskRecord("jobTaskDone", 1, -60);
        insertTaskRecord("jobTaskPrized", 2, -60);
        // 还没到期的进行中记录
        insertTaskRecord("jobTaskRunning", 0, 60);

        assertEquals(expirableBefore + 2, taskRecordDao.countExpirableRecord(now),
                "待收口条数没有把新造的两条算进去");

        int rows = taskRecordDao.expireRecordBatch(now, 100000);
        assertEquals(expirableBefore + 2, rows, "收口影响的行数与「待收口」对不上");
        assertEquals(1, count("SELECT COUNT(*) FROM t_task_record"
                        + " WHERE member_id = " + fakeMemberId("jobTaskDone") + " AND status = 1"),
                "已完成的记录被收口任务动了 —— 用户已经达标，动它等于把奖吞掉");
        assertEquals(1, count("SELECT COUNT(*) FROM t_task_record"
                        + " WHERE member_id = " + fakeMemberId("jobTaskPrized") + " AND status = 2"),
                "已发奖的记录被收口任务动了");
        assertEquals(1, count("SELECT COUNT(*) FROM t_task_record"
                        + " WHERE member_id = " + fakeMemberId("jobTaskRunning") + " AND status = 0"),
                "还没到期的记录被收口了");
        assertEquals(0, taskRecordDao.expireRecordBatch(now, 100000), "第二次执行还在改行，说明不幂等");
    }

    // ==================== 提案卡单扫描 ====================

    @Test
    @DisplayName("卡单扫描：条数与样本口径一致，且样本按卡得最久的排前面")
    void stuckScanCountAndSampleAgree() {
        LocalDateTime now = dbNow();
        int minutes = 30;

        long expected = count("SELECT COUNT(*) FROM t_proposal_record WHERE status IN (30, 40)"
                + " AND update_time < DATE_SUB(now(), INTERVAL 30 MINUTE)");
        long actual = proposalRecordDao.countStuckDispatch(now, minutes);
        assertEquals(expected, actual, "卡单条数与库里对不上");

        List<Map<String, Object>> samples = proposalRecordDao.selectStuckDispatchSample(now, minutes, 10);
        assertEquals(Math.min(10L, expected), samples.size(), "样本条数不对");
        if (!samples.isEmpty()) {
            assertEquals(java.util.Set.of("tradeNo", "status", "memberId", "memberName", "stuckMinutes"),
                    samples.get(0).keySet(),
                    "SQL 别名与 Job 读取的 key 对不上：日志里会打出一串 null");
            long previous = Long.MAX_VALUE;
            for (Map<String, Object> row : samples) {
                long stuck = ((Number) row.get("stuckMinutes")).longValue();
                assertTrue(stuck <= previous, "样本没有按卡得最久的排前面");
                assertTrue(stuck >= minutes, "样本里混进了没到阈值的提案");
                previous = stuck;
            }
        }
    }
}
