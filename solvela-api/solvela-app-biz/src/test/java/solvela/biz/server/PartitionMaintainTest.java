package solvela.biz.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.base.partition.MonthlyPartition;
import solvela.base.partition.PartitionMaintainService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分区维护：<b>对着真 MySQL 跑真 DDL</b>。
 *
 * <h3>为什么必须是集成测试</h3>
 * 这段代码唯一的难点全在 MySQL 自己的规矩上：{@code REORGANIZE} 要求区间连续递增、
 * 必须覆盖原 {@code pmax} 的范围、末尾必须再补一个 {@code pmax}。
 * 这些约束<b>拿 mock 的 JdbcTemplate 一条都验不出来</b> —— 它会老老实实「执行」任何字符串。
 * 而写错的后果是建表之后某天突然插不进数据。
 *
 * <p>所有操作都在一张自己建的探针表上做，跑完就删，不碰任何业务表。
 *
 * @Date 2026-10-16
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@DisplayName("按月分区维护")
class PartitionMaintainTest {

    private static final String PROBE = "t_part_probe";

    /** 探针表的「当前月」。固定下来，免得用例的行为跟着真实日期变 */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Autowired
    private PartitionMaintainService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createProbe() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + PROBE);
        // 只建到 2026-10，后面全靠任务补 —— 这正是三张业务表现在的处境
        jdbcTemplate.execute("""
                CREATE TABLE t_part_probe (
                  id bigint NOT NULL AUTO_INCREMENT,
                  create_time datetime NOT NULL,
                  PRIMARY KEY (id, create_time)
                ) ENGINE=InnoDB
                PARTITION BY RANGE COLUMNS(create_time) (
                  PARTITION p202604 VALUES LESS THAN ('2026-05-01'),
                  PARTITION p202605 VALUES LESS THAN ('2026-06-01'),
                  PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
                  PARTITION pmax VALUES LESS THAN (MAXVALUE)
                )
                """);
    }

    @AfterEach
    void dropProbe() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + PROBE);
    }

    @Test
    @DisplayName("🔴 补出缺的月份，而且补完之后数据进得去 —— pmax 必须还在")
    void addsMissingMonths() {
        // 提前 2 个月：当月 10 已有，要补 11、12
        PartitionMaintainService.Outcome outcome = service.maintain(PROBE, 2, 6, 0, TODAY, false);

        assertEquals(List.of("p202611", "p202612"), outcome.added());
        assertTrue(partitions().contains(MonthlyPartition.MAX_PARTITION), "兜底分区不能被重组掉");

        // 真插几行：落在新分区里的、以及比最后一个分区还新的（那一行要靠 pmax 接住）
        insert("2026-11-15 10:00:00");
        insert("2026-12-31 23:59:59");
        insert("2030-01-01 00:00:00");
        assertEquals(3, count(), "🔴 插不进去就说明区间断了或者 pmax 没补回来");
    }

    @Test
    @DisplayName("过期分区整个删掉（不是 DELETE），里面的数据跟着走")
    void dropsExpired() {
        insert("2026-04-10 00:00:00");
        insert("2026-05-10 00:00:00");
        insert("2026-10-10 00:00:00");
        assertEquals(3, count());

        // 保留 6 个月 = 当月连同之前 5 个月（2026-05 ~ 2026-10）。
        // 截止 2026-05-01，上界 <= 它的才算过期 —— 只有 p202604
        PartitionMaintainService.Outcome outcome = service.maintain(PROBE, 0, 6, 3, TODAY, false);

        assertEquals(List.of("p202604"), outcome.dropped());
        assertFalse(partitions().contains("p202604"));
        assertEquals(2, count(), "4 月那一行跟着分区一起消失");
        assertTrue(partitions().contains("p202605"), "5 月还在保留期内");
    }

    @Test
    @DisplayName("🔴 单次删除有上限：保留期被误配时，不会一口气把几年的数据抹掉")
    void dropIsCapped() {
        // 保留 1 个月 → 9 月之前的全过期（p202604、p202605），但上限 1
        PartitionMaintainService.Outcome outcome = service.maintain(PROBE, 0, 1, 1, TODAY, false);

        assertEquals(1, outcome.dropped().size());
        assertEquals("p202604", outcome.dropped().get(0), "先删最旧的");
        assertTrue(partitions().contains("p202605"), "剩下的下次接着删");
    }

    @Test
    @DisplayName("试运行只报告，不动结构")
    void dryRunChangesNothing() {
        List<String> before = partitions();

        PartitionMaintainService.Outcome outcome = service.maintain(PROBE, 2, 1, 3, TODAY, true);

        assertFalse(outcome.added().isEmpty());
        assertFalse(outcome.dropped().isEmpty());
        assertEquals(before, partitions());
    }

    @Test
    @DisplayName("幂等：连跑两次，第二次什么都不做")
    void idempotent() {
        service.maintain(PROBE, 2, 6, 3, TODAY, false);
        List<String> after = partitions();

        PartitionMaintainService.Outcome second = service.maintain(PROBE, 2, 6, 3, TODAY, false);

        assertTrue(second.added().isEmpty());
        assertTrue(second.dropped().isEmpty());
        assertEquals(after, partitions());
    }

    @Test
    @DisplayName("🔴 不是按 create_time 分区的表一律跳过 —— 不去猜它该怎么分")
    void skipsForeignShapes() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_part_probe_plain");
        jdbcTemplate.execute("CREATE TABLE t_part_probe_plain (id bigint PRIMARY KEY)");
        try {
            assertTrue(service.maintain("t_part_probe_plain", 2, 6, 3, TODAY, false).skipped(),
                    "没有分区的表不该被这个任务动 —— 给它加分区是一次迁移，不是日常维护");
        } finally {
            jdbcTemplate.execute("DROP TABLE IF EXISTS t_part_probe_plain");
        }

        assertTrue(service.maintain("t_table_does_not_exist", 2, 6, 3, TODAY, false).skipped());
        assertTrue(service.maintain("t_member; DROP TABLE x", 2, 6, 3, TODAY, false).skipped(),
                "🔴 表名形状不合法就拒绝 —— DDL 没法用占位符，这是那条防线");
    }

    @Test
    @DisplayName("名字与上界对不上的分区不动它（有人手工改过的情况）")
    void leavesMismatchedPartitionsAlone() {
        /*
         * 把 4 月、5 月两个分区合成一个，名字仍叫 p202604 —— 于是「名字说 4 月、实际装着 4~5 月」。
         * （合并相邻分区是 MySQL 允许的重组：区间总和没变。）
         */
        jdbcTemplate.execute("ALTER TABLE " + PROBE
                + " REORGANIZE PARTITION p202604, p202605 INTO ("
                + "PARTITION p202604 VALUES LESS THAN ('2026-06-01'))");

        // 保留 1 个月 → 按上界算这个分区早就过期了，但名字对不上，所以不能动
        PartitionMaintainService.Outcome outcome = service.maintain(PROBE, 0, 1, 3, TODAY, false);

        assertTrue(outcome.dropped().isEmpty(),
                "按名字去删一个区间被改过的分区，删掉的就是别的月份的数据");
        assertTrue(partitions().contains("p202604"));
    }

    @Test
    @DisplayName("业务表现在的分区形状是本任务认得的 —— 否则它会一直静默跳过")
    void realTablesAreRecognised() {
        for (String table : List.of("t_member_login_log", "t_member_risk_event", "t_member_risk_hit")) {
            PartitionMaintainService.Outcome outcome = service.maintain(table, 2, 6, 0, TODAY, true);
            assertFalse(outcome.skipped(),
                    table + " 被跳过了：" + outcome.skippedReason() + " —— 它的分区就不会有人维护");
        }
    }

    private void insert(String createTime) {
        jdbcTemplate.update("INSERT INTO " + PROBE + " (create_time) VALUES (?)", createTime);
    }

    private int count() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + PROBE, Integer.class);
        return n == null ? 0 : n;
    }

    private List<String> partitions() {
        return jdbcTemplate.queryForList("""
                SELECT partition_name FROM information_schema.partitions
                 WHERE table_schema = DATABASE() AND table_name = ?
                 ORDER BY partition_ordinal_position
                """, String.class, PROBE);
    }

    /** 让 {@link YearMonth} 的导入有用处：命名约定变了这里会先编译不过 */
    @Test
    @DisplayName("分区命名约定：p202610 装到 2026-11-01（不含）")
    void namingConvention() {
        assertEquals("p202610", MonthlyPartition.nameOf(YearMonth.of(2026, 10)));
        assertEquals(LocalDate.of(2026, 11, 1), MonthlyPartition.upperBound(YearMonth.of(2026, 10)));
        assertEquals(YearMonth.of(2026, 10), MonthlyPartition.monthOf("p202610"));
        assertEquals(null, MonthlyPartition.monthOf("pmax"));
        assertEquals(LocalDate.of(2026, 11, 1), MonthlyPartition.boundaryOf("'2026-11-01'"));
        assertEquals(null, MonthlyPartition.boundaryOf("MAXVALUE"));
    }
}
