package solvela.admin.task;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 把一个测试会员在<b>所有</b>会员维度表里的行删干净。
 *
 * <h3>🔴 为什么需要它</h3>
 * {@code TaskRuntimeP0AcceptanceTest} 每个用例都真往 {@code t_member} 插一行
 *（{@code report()} 会校验会员真实存在，编号不行），而它<b>不能用事务回滚</b>——
 * 它要验的正是 {@code @TransactionalEventListener(AFTER_COMMIT)} + 异步派奖，
 * 回滚掉就什么都测不到了。
 *
 * <p>于是每跑一次全量测试，开发库就多 58 个 {@code p0_} 会员和他们的全部下游数据。
 * 2026-09-24 清理时数出来 811 个 —— 大约是跑了 14 次全量的沉淀。
 * 清一次只是把计数器归零，不装这个，下周又是几百个。
 *
 * <h3>⚠️ 表清单在运行时从 information_schema 查，不写死</h3>
 * 「哪些表有 member_id」这个问题，靠人维护的清单<b>已经错过三次</b>
 *（见 {@code 工具-清理联调会员数据.sql} 里那段注释：{@code t_announcement_ack} 那批、
 * {@code t_external_order}、{@code t_grade_entitlement_grant}，三次原因一模一样 ——
 * 建了新表却没想起还有一份清单要补）。
 *
 * <p>查一次 information_schema 就永远不会漏。代价是每个用例多一次元数据查询，
 * 而它是缓存过的，比漏删一张表便宜得多。
 *
 * @author alaric
 * @date 2026-09-24
 */
final class TestMemberCleaner {

    /**
     * 手工备份表（{@code <原表名>_dup_<日期>} 之类）不碰。
     * 它们是某人某天的快照，不该被测试改写 —— 判据与 {@code DumpSchema} 那份一致。
     */
    private static final String BACKUP_PATTERN = ".+_(dup|bak|backup)_[0-9]{6,8}$";

    private TestMemberCleaner() {
    }

    /**
     * 等异步线程池把活干完。
     *
     * <h3>🔴 为什么不能只 sleep 一个固定值</h3>
     * 派奖走 {@code @TransactionalEventListener(AFTER_COMMIT)} + 异步池。
     * 先试过 {@code sleep(1500)} 然后删 —— 结果留下 300 多行<b>孤儿</b>：
     * 会员行删掉之后，还在飞的那些活才落库，于是变成「查不到主人的账」。
     * 而那正是这份清理要消灭的东西，方向还反了。
     *
     * <p>固定 sleep 的本质是拿一个猜的数字赌线程池的速度，机器一慢就赌输，
     * 而输的表现是脏数据，不是用例变红 —— 没有人会发现。
     *
     * <p>这里改成<b>盯着活跃线程数和队列长度</b>：都归零才算干完。
     *
     * @param pools 要等的池子。传 {@code null} 或非 ThreadPoolTaskExecutor 的直接跳过
     */
    static void awaitIdle(long timeoutMillis, Object... pools) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (allIdle(pools)) {
                // 再给一拍：活跃数归零只说明线程跑完了，最后那次写库的事务可能刚提交
                Thread.sleep(300);
                if (allIdle(pools)) {
                    return;
                }
            }
            Thread.sleep(100);
        }
    }

    private static boolean allIdle(Object... pools) {
        for (Object pool : pools) {
            if (pool instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor p) {
                if (p.getActiveCount() > 0 || !p.getThreadPoolExecutor().getQueue().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 删掉这个会员的全部数据。
     *
     * <p>⚠️ 这个库<b>刻意不建外键</b>（见各表 DDL 的注释），所以删除顺序无关紧要，
     * 也不会因为顺序不对而失败。反过来说：<b>数据库不会帮我们级联</b>，
     * 漏一张表就留下一行查不到主人的账，而它当场不报错。
     */
    static void purge(JdbcTemplate jdbc, long memberId) {
        for (String table : memberScopedTables(jdbc)) {
            jdbc.update("DELETE FROM " + table + " WHERE member_id = ?", memberId);
        }
    }

    /** 当前库里所有带 member_id 的表。t_member 放最后，读起来顺一点（无外键，顺序不影响结果） */
    private static List<String> memberScopedTables(JdbcTemplate jdbc) {
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.COLUMNS "
                        + " WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'member_id' "
                        + " ORDER BY CASE WHEN TABLE_NAME = 't_member' THEN 1 ELSE 0 END, TABLE_NAME",
                String.class);
        return tables.stream().filter(t -> !t.matches(BACKUP_PATTERN)).toList();
    }
}
