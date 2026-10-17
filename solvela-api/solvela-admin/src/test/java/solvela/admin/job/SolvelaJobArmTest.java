package solvela.admin.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import solvela.admin.module.system.job.config.SolvelaJobConfig;
import solvela.admin.module.system.job.repository.SolvelaJobDao;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「从没排过期」的任务在启动时被排上（{@code SolvelaJobBootstrapInitializer} → {@link SolvelaJobDao#armUnscheduled}）。
 *
 * <h3>🔴 它守的是一件没有任何报错的事</h3>
 * 种子数据里的任务是直接写进库的，{@code next_trigger_time} 为空；扫描器只挑有下次触发时间的行，
 * 于是空库装完之后，分区维护、网关快照重发、安全事件落库、名单维护<b>一次都不会跑</b> ——
 * 2026-10-17 端到端验收时，安全事件在 Redis 里堆了 76 条、一条都没落库，才发现这件事。
 *
 * <p>不该被排上的：停用的、终止的（{@code terminal_flag = 1}，它的 NULL 是刻意的）、一次性任务。
 * 整个用例在事务里，结束时回滚。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
@Transactional
class SolvelaJobArmTest {

    @Autowired
    private SolvelaJobDao jobDao;

    @Autowired
    private SolvelaJobConfig jobConfig;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("🔴 启用的 cron 任务没有下次触发时间 → 排上；停用的、终止的、一次性的不动")
    void armsOnlyUnscheduledCronJobs() {
        String base = "ARM" + ThreadLocalRandom.current().nextInt(1_000_000);
        insert(base + "A", "cron", true, false);
        insert(base + "B", "cron", false, false);
        insert(base + "C", "cron", true, true);
        insert(base + "D", "one_time", true, false);

        int armed = jobDao.armUnscheduled(jobConfig.getEnv());

        assertTrue(armed >= 1);
        assertNotNull(next(base + "A"), "启用的 cron 任务要排上，否则它永远不会跑");
        assertNull(next(base + "B"), "停用的不排");
        assertNull(next(base + "C"), "终止的不排：它的 NULL 是刻意的");
        assertNull(next(base + "D"), "一次性任务的下次触发就是它的目标时刻，不由这里定");
        assertEquals(0, jobDao.armUnscheduled(jobConfig.getEnv()), "排过一次之后没有可排的了 —— 已有下次触发时间的不会被改写");
    }

    private void insert(String code, String triggerType, boolean enabled, boolean terminal) {
        jdbcTemplate.update("""
                INSERT INTO t_solvela_job (job_code, job_name, handler_name, job_group, trigger_type, trigger_value,
                    next_trigger_time, trigger_version, jitter_seconds, enabled_flag, preset_code, timeout_seconds,
                    retry_times, retry_interval, misfire_strategy, misfire_threshold_sec, block_strategy, sort, remark,
                    deleted_flag, update_name, app_env, continuous_fail_count, handler_missing_flag, terminal_flag,
                    owner_biz_type, source, manual_modified_flag)
                VALUES (?, ?, 'gatewaySnapshotPublish', 'SYSTEM', ?, ?, NULL, 0, 0, ?, 'NORMAL', 30, 0, 60, 'SKIP', 300,
                    'DISCARD', 0, '用例', 0, 'test', ?, 0, 0, ?, 'SYSTEM', 'SYSTEM', 0)""",
                code, "用例 " + code, triggerType, "one_time".equals(triggerType) ? "2099-01-01 00:00:00" : "0 0 3 * * *",
                enabled ? 1 : 0, jobConfig.getEnv(), terminal ? 1 : 0);
    }

    private Object next(String code) {
        return jdbcTemplate.queryForObject("SELECT next_trigger_time FROM t_solvela_job WHERE job_code = ?", Object.class, code);
    }
}
