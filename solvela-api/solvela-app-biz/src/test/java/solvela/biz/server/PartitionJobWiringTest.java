package solvela.biz.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.partition.PartitionMaintainJob;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分区维护任务<b>装得上、而且真能跑</b>。
 *
 * <h3>为什么要单独钉这件事</h3>
 * 本项目已经为此交过一次学费：{@code JOBEXTEXP} 的 handler 在一个 admin 不依赖的模块里，
 * 于是<b>库里有行、代码里有 handler，两边却永远见不到面</b> —— 调度看门狗每 5 分钟报一次
 * 「执行器失联」，而那条任务躺了一周没人知道。
 *
 * <p>这个任务的 handler 在 {@code solvela-base-data}（所有进程都依赖它），
 * 但「依赖得到」和「真的注册上了」是两件事，所以这里直接把它跑一遍。
 *
 * <p>⚠️ 用 {@code dryRun} 跑：本用例不该改线上表的结构。
 * 真实 DDL 由 {@code PartitionMaintainTest} 在一张探针表上验。
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@DisplayName("分区维护任务的装配")
class PartitionJobWiringTest {

    @Autowired
    private PartitionMaintainJob job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("任务跑得起来，默认参数下不报错")
    void jobRuns() {
        LocalDateTime now = jdbcTemplate.queryForObject("SELECT NOW()", LocalDateTime.class);
        assertNotNull(now);

        String result = job.execute(new SolvelaJobContext(
                "JOBPARTMNT", "【基础】按月分区维护", "partitionMaintain", "{\"dryRun\":true}",
                now.toLocalDate(), now, now, "test-trace", null, 0, 0, 1, () -> false));

        assertNotNull(result);
        assertFalse(result.contains("失败"), "实际结果：" + result);
    }

    @Test
    @DisplayName("🔴 库里登记了这条任务，而且 handler_name 与代码对得上、是启用的")
    void registeredAndEnabled() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT handler_name, enabled_flag, trigger_value, handler_missing_flag
                  FROM t_solvela_job
                 WHERE handler_name = 'partitionMaintain' AND deleted_flag = 0
                """);

        assertEquals(1, rows.size(), "t_solvela_job 里没有这条任务 —— 它永远不会被调度到");
        Map<String, Object> row = rows.get(0);
        // tinyint(1) 会被 JDBC 映射成 Boolean，别按 Number 取
        assertTrue(flag(row.get("enabled_flag")), "🔴 停用的话分区就没人补了，而那件事是静默的");
        assertFalse(flag(row.get("handler_missing_flag")),
                "看门狗认为 handler 失联 —— 检查 admin 是否依赖得到它所在的模块");
        assertTrue(String.valueOf(row.get("trigger_value")).startsWith("0 "),
                "cron 形状不对：" + row.get("trigger_value"));
    }

    private static boolean flag(Object value) {
        return value instanceof Boolean b ? b : ((Number) value).intValue() != 0;
    }
}
