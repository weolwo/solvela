package solvela.admin.module.system.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import solvela.admin.module.system.job.constant.SolvelaJobExecuteStatusEnum;
import solvela.admin.module.system.job.repository.SolvelaJobRepository;
import solvela.admin.module.system.job.repository.domain.SolvelaJobLogEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行器与僵尸扫描抢着给同一条「执行中」记录落终态，在真库上走一遍。
 *
 * <p>以前两边都是无条件 updateById：扫描读到执行中之后执行器恰好跑完，扫描会把「成功」盖成「中断」、
 * 再插一条重试 —— 幂等任务多跑一遍，连续失败数 +1 触发告警。现在先到的一方生效，晚到的什么都不做。
 * 任务 id 用一个不存在的值，不碰库里真实任务的计数；数据结束时回滚。
 */
@SpringBootTest
@Transactional
class SolvelaJobFinishRaceTest {

    private static final int FAKE_JOB_ID = 999_999_001;

    @Autowired
    private SolvelaJobRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("🔴 执行器先写了成功，僵尸扫描晚到：不改终态、不插重试")
    void 扫描晚到不覆盖成功() {
        long logId = insertRunning();

        assertTrue(repository.finishAndScheduleRetry(terminal(logId, SolvelaJobExecuteStatusEnum.SUCCESS), null, FAKE_JOB_ID, 0, logId));
        assertFalse(repository.finishAndScheduleRetry(terminal(logId, SolvelaJobExecuteStatusEnum.INTERRUPTED),
                retryOf(logId), FAKE_JOB_ID, 1, logId), "晚到的僵尸扫描仍然生效了");

        assertEquals(SolvelaJobExecuteStatusEnum.SUCCESS.getValue(), statusOf(logId), "成功被盖成了中断");
        assertEquals(0, retriesOf(logId), "成功的执行被多排了一次重试");
    }

    @Test
    @DisplayName("僵尸扫描先判了中断，执行器晚到：不覆盖扫描的结论")
    void 执行器晚到不覆盖中断() {
        long logId = insertRunning();

        assertTrue(repository.finishAndScheduleRetry(terminal(logId, SolvelaJobExecuteStatusEnum.INTERRUPTED),
                retryOf(logId), FAKE_JOB_ID, 1, logId));
        assertFalse(repository.finishAndScheduleRetry(terminal(logId, SolvelaJobExecuteStatusEnum.SUCCESS), null, FAKE_JOB_ID, 0, logId));

        assertEquals(SolvelaJobExecuteStatusEnum.INTERRUPTED.getValue(), statusOf(logId));
        assertEquals(1, retriesOf(logId), "先到的扫描排的那一次重试应当在");
    }

    private long insertRunning() {
        jdbcTemplate.update("""
                INSERT INTO t_solvela_job_log (job_id, job_name, trigger_time, status, execute_start_time, create_name)
                VALUES (?, 'zz-race', NOW(), ?, NOW(), 'test')
                """, FAKE_JOB_ID, SolvelaJobExecuteStatusEnum.RUNNING.getValue());
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? -1 : id;
    }

    private static SolvelaJobLogEntity terminal(long logId, SolvelaJobExecuteStatusEnum status) {
        SolvelaJobLogEntity log = new SolvelaJobLogEntity();
        log.setLogId(logId);
        log.setStatus(status);
        log.setExecuteEndTime(LocalDateTime.now());
        return log;
    }

    private static SolvelaJobLogEntity retryOf(long logId) {
        SolvelaJobLogEntity retry = new SolvelaJobLogEntity();
        retry.setJobId(FAKE_JOB_ID);
        retry.setJobName("zz-race");
        retry.setTriggerTime(LocalDateTime.now());
        retry.setRetrySeq(1);
        retry.setRetryOfLogId(logId);
        retry.setStatus(SolvelaJobExecuteStatusEnum.PENDING);
        retry.setFireTime(LocalDateTime.now().plusMinutes(5));
        retry.setCreateName("system");
        return retry;
    }

    private Integer statusOf(long logId) {
        return jdbcTemplate.queryForObject("SELECT status FROM t_solvela_job_log WHERE log_id = ?", Integer.class, logId);
    }

    private Integer retriesOf(long logId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_solvela_job_log WHERE retry_of_log_id = ?", Integer.class, logId);
    }
}
