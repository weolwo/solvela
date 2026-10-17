package solvela.base.partition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 按月分区表的维护：提前把下几个月的分区建出来，把过期的整个删掉。
 *
 * <h3>🔴 为什么必须有人管它</h3>
 * 分区是在建表时一次性写死的（本项目的几张表只建到 2027-01）。没人补的话，
 * 那之后所有数据都落进兜底分区 {@code pmax} —— 它<b>永远不会过期</b>，于是：
 * <ul>
 *   <li>保留期形同虚设，表只会一直涨；</li>
 *   <li>想清的时候已经清不动了 —— 一个分区里混着几年的数据，只能 DELETE，
 *       而那意味着长事务、主从延迟、留一堆碎片。</li>
 * </ul>
 * 而这一切<b>没有任何症状</b>，直到某天写入开始变慢。
 *
 * <h3>删用 DROP PARTITION，不用 DELETE</h3>
 * 删一个分区是改元数据，瞬间完成、立刻释放磁盘。按时间 DELETE 几百万行是完全不同的一件事。
 *
 * <h3>🔴 三道闸，每一道都是为了「别删错东西」</h3>
 * 这段代码有删数据的能力，而删掉的东西<b>找不回来</b>。所以：
 * <ol>
 *   <li><b>只认按 {@code create_time} 做 RANGE COLUMNS 的表</b>，而且必须有 {@code pmax}。
 *       传进来一个不符合的表名一律拒绝 —— 这同时也挡住了「表名拼进 DDL」这件事本身的风险：
 *       DDL 没法用占位符，而能通过校验的表名已经被 {@code information_schema} 认过一遍；</li>
 *   <li><b>只删名字形如 {@code pYYYYMM}、而且上界早于截止月</b>的分区。{@code pmax} 与看不懂的名字一概不动；</li>
 *   <li><b>一次最多删几个</b>。保留期被人误配成 1 个月时，它一次只能删掉几个月，
 *       而不是把几年的数据一口气抹掉 —— 而且每次都会 WARN。</li>
 * </ol>
 *
 * @Date 2026-10-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartitionMaintainService {

    /** 表名的形状。库里查得到才算真存在，这里只是先挡掉明显不是表名的东西 */
    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    /** 分区列只认它 —— 维护的几张表都是按它分的，而「按哪一列分」决定了边界怎么算 */
    private static final String PARTITION_COLUMN = "create_time";

    private final JdbcTemplate jdbcTemplate;

    /** 一张表这一次做了什么 */
    public record Outcome(String table, List<String> added, List<String> dropped, String skippedReason) {

        public boolean skipped() {
            return skippedReason != null;
        }

        public boolean changed() {
            return !added.isEmpty() || !dropped.isEmpty();
        }
    }

    /**
     * 维护一张表。
     *
     * @param aheadMonths  提前建几个月（含当月）
     * @param retainMonths 保留几个月：上界早于「当月往前推这么多个月」的分区会被删掉
     * @param maxDrop      这一次最多删几个分区
     * @param today        今天（由调用方从<b>数据库时钟</b>取，别用 JVM 的）
     * @param dryRun       只算不做
     */
    public Outcome maintain(String table, int aheadMonths, int retainMonths, int maxDrop,
                            LocalDate today, boolean dryRun) {
        if (table == null || !TABLE_NAME.matcher(table).matches()) {
            return skip(table, "表名形状不合法");
        }
        Map<String, LocalDate> partitions = readPartitions(table);
        if (partitions == null) {
            return skip(table, "不是按 " + PARTITION_COLUMN + " 做 RANGE COLUMNS 分区的表，或者没有 "
                    + MonthlyPartition.MAX_PARTITION + " 兜底分区");
        }

        YearMonth current = YearMonth.from(today);
        List<YearMonth> missing = new ArrayList<>();
        for (int i = 0; i <= aheadMonths; i++) {
            YearMonth month = current.plusMonths(i);
            if (!partitions.containsKey(MonthlyPartition.nameOf(month))) {
                missing.add(month);
            }
        }

        List<String> expired = expired(partitions, current, retainMonths, maxDrop, table);

        if (dryRun) {
            return new Outcome(table,
                    missing.stream().map(MonthlyPartition::nameOf).toList(), expired, null);
        }

        List<String> added = missing.isEmpty() ? List.of() : addPartitions(table, missing);
        List<String> dropped = expired.isEmpty() ? List.of() : dropPartitions(table, expired);
        return new Outcome(table, added, dropped, null);
    }

    /**
     * 读这张表的分区，顺便校验它是不是我们认得的那种。
     *
     * @return 分区名 → 上界（{@code pmax} 的上界是 null）；表不符合要求时返回 null
     */
    private Map<String, LocalDate> readPartitions(String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT partition_name, partition_description, partition_method, partition_expression
                  FROM information_schema.partitions
                 WHERE table_schema = DATABASE() AND table_name = ?
                 ORDER BY partition_ordinal_position
                """, table);
        if (rows.isEmpty() || rows.get(0).get("partition_name") == null) {
            // 没有分区（或者表不存在）。不去给它建分区 —— 那是一次迁移，不是日常维护
            return null;
        }
        Map<String, LocalDate> partitions = new LinkedHashMap<>();
        boolean hasMax = false;
        for (Map<String, Object> row : rows) {
            String method = String.valueOf(row.get("partition_method"));
            String expression = String.valueOf(row.get("partition_expression"));
            if (!"RANGE COLUMNS".equalsIgnoreCase(method)
                    || !expression.replace("`", "").trim().equalsIgnoreCase(PARTITION_COLUMN)) {
                return null;
            }
            String name = String.valueOf(row.get("partition_name"));
            partitions.put(name, MonthlyPartition.boundaryOf(String.valueOf(row.get("partition_description"))));
            hasMax |= MonthlyPartition.MAX_PARTITION.equals(name);
        }
        return hasMax ? partitions : null;
    }

    /**
     * 该删哪些。
     *
     * <p>判据是<b>上界</b>而不是名字里的月份：一个名字叫 {@code p202601} 却装着别的区间的分区
     * （有人手工改过）不该按名字去删。两者都要对得上。
     */
    private List<String> expired(Map<String, LocalDate> partitions, YearMonth current,
                                 int retainMonths, int maxDrop, String table) {
        /*
         * 🔴 减的是 retainMonths - 1，不是 retainMonths。
         *
         * 「保留 6 个月」的意思是<b>当月连同之前 5 个月</b>都留着 —— 当月自己占掉一个月的额度。
         * 写成 minusMonths(retainMonths) 会多留一个月：保留期配 6、实际留 7，
         * 而「保留 1 个月」会变成「连上个月也留着」，那就不是任何人以为的意思了。
         *
         * 截止日是「最旧那个要留的月份的 1 号」：上界 <= 它的分区，里面的数据全都更早。
         */
        LocalDate cutoff = current.minusMonths(retainMonths - 1L).atDay(1);
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, LocalDate> entry : partitions.entrySet()) {
            String name = entry.getKey();
            LocalDate bound = entry.getValue();
            if (MonthlyPartition.MAX_PARTITION.equals(name) || bound == null) {
                continue;
            }
            YearMonth named = MonthlyPartition.monthOf(name);
            if (named == null || !MonthlyPartition.upperBound(named).equals(bound)) {
                log.warn("【分区维护】{} 的分区 {} 名字与上界({})对不上，不动它", table, name, bound);
                continue;
            }
            // 上界 <= 截止日 ⇒ 这个分区里的数据全都早于保留期
            if (!bound.isAfter(cutoff)) {
                expired.add(name);
            }
        }
        if (expired.size() > maxDrop) {
            log.warn("【分区维护】{} 有 {} 个过期分区，本次只删最旧的 {} 个 —— "
                            + "保留期是不是配小了？剩下的下次接着删", table, expired.size(), maxDrop);
            return expired.subList(0, maxDrop);
        }
        return expired;
    }

    /**
     * 补分区。
     *
     * <h3>🔴 必须一条 REORGANIZE 把新分区和 pmax 一起重写，而且按升序</h3>
     * MySQL 要求重组之后的分区区间<b>连续且递增</b>，并且覆盖原来 {@code pmax} 的范围。
     * 分几条语句做、或者顺序乱了，都会直接报错（这还算好事）；
     * 而漏掉末尾的 {@code pmax} 会让「比最后一个分区还新的数据」<b>无处可去</b> ——
     * 那时插入会失败，表现是业务突然写不进去。
     *
     * <p>⚠️ 重组会重写当前在 {@code pmax} 里的行。正常情况它是空的（提前建就是为了这个），
     * 所以很快；真有数据落进去过，这一步会慢一些，但它仍然是对的。
     */
    private List<String> addPartitions(String table, List<YearMonth> months) {
        List<YearMonth> sorted = new ArrayList<>(months);
        sorted.sort(YearMonth::compareTo);
        StringBuilder sql = new StringBuilder("ALTER TABLE `").append(table)
                .append("` REORGANIZE PARTITION ").append(MonthlyPartition.MAX_PARTITION).append(" INTO (");
        List<String> names = new ArrayList<>(sorted.size());
        for (YearMonth month : sorted) {
            String name = MonthlyPartition.nameOf(month);
            names.add(name);
            sql.append("PARTITION ").append(name)
                    .append(" VALUES LESS THAN ('").append(MonthlyPartition.upperBound(month)).append("'), ");
        }
        sql.append("PARTITION ").append(MonthlyPartition.MAX_PARTITION).append(" VALUES LESS THAN (MAXVALUE))");
        jdbcTemplate.execute(sql.toString());
        log.info("【分区维护】{} 新增分区 {}", table, names);
        return names;
    }

    private List<String> dropPartitions(String table, List<String> names) {
        // 一条语句删掉多个：每条一次 ALTER 的话，中间失败会留下一个「删了一半」的状态
        jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP PARTITION " + String.join(", ", names));
        log.info("【分区维护】{} 删除过期分区 {}", table, names);
        return names;
    }

    private static Outcome skip(String table, String reason) {
        log.warn("【分区维护】跳过 {}：{}", table, reason);
        return new Outcome(table, List.of(), List.of(), reason);
    }
}
