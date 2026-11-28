package solvela.admin.module.system.job;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.admin.module.system.job.constant.SolvelaJobExecuteStatusEnum;
import solvela.admin.module.system.job.repository.SolvelaJobLogDao;
import solvela.admin.module.system.job.repository.domain.SolvelaJobLogEntity;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务执行日志 SQL 里的状态判据。
 *
 * <p>最要紧的是 {@code finishRunning} 只认执行中：执行器与僵尸扫描都会落终态，无条件写回时晚到的一方会盖掉先到的
 * （成功被记成中断、幂等任务多跑一次）。抢占、计数、僵尸扫描也都按状态判，状态值写成 {@code ${@...@X.value}}，
 * 常量改名会在渲染这一步抛异常。不连库。
 */
class SolvelaJobLogStatusSqlTest {

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/support/SolvelaJobLogMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static final String RUNNING = "status = " + SolvelaJobExecuteStatusEnum.RUNNING.getValue();
    private static final String PENDING = "status = " + SolvelaJobExecuteStatusEnum.PENDING.getValue();

    @Test
    @DisplayName("🔴 落终态只认执行中")
    void 落终态只认执行中() {
        String sql = sqlOf("finishRunning");
        assertTrue(sql.contains("WHERE log_id = ? AND " + RUNNING), "落终态必须带「仍是执行中」：" + sql);
    }

    @Test
    @DisplayName("抢占：待执行 → 执行中；计数与僵尸扫描都只看执行中")
    void 其它状态判据() {
        String preempt = sqlOf("preemptPendingLog");
        assertTrue(preempt.contains("SET status = " + SolvelaJobExecuteStatusEnum.RUNNING.getValue()), preempt);
        assertTrue(preempt.contains("AND " + PENDING), preempt);
        assertTrue(sqlOf("countRunning").contains("AND " + RUNNING), sqlOf("countRunning"));
        assertTrue(sqlOf("selectZombieList").contains("WHERE " + RUNNING), sqlOf("selectZombieList"));
    }

    private static String sqlOf(String id) {
        SolvelaJobLogEntity log = new SolvelaJobLogEntity();
        log.setLogId(1L);
        log.setStatus(SolvelaJobExecuteStatusEnum.SUCCESS);
        Map<String, Object> param = new HashMap<>();
        param.put("log", log);
        param.put("logId", 1L);
        param.put("ip", "127.0.0.1");
        param.put("startTime", LocalDateTime.now());
        param.put("jobId", 1);
        param.put("earliestStartTime", LocalDateTime.now());
        param.put("beforeTime", LocalDateTime.now());
        param.put("limit", 10);
        String sql = configuration.getMappedStatement(SolvelaJobLogDao.class.getName() + "." + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
