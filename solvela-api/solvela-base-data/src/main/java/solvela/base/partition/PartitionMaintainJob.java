package solvela.base.partition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 按月分区表的分区维护：提前补下几个月，删掉过期的。
 *
 * <h3>它补的是一个一直存在的缺口</h3>
 * 三张按月分区的表（{@code t_member_login_log}、{@code t_member_risk_event}、
 * {@code t_member_risk_hit}）的分区都是建表时一次性写死的，只建到 2027-01。
 * <b>在本任务之前没有任何东西会去补</b> —— 到那之后所有数据落进 {@code pmax}，
 * 而它永不过期：保留期形同虚设，表只会一直涨，想清的时候已经清不动了（只能 DELETE）。
 * 这件事没有任何症状，直到某天写入开始变慢。
 *
 * <h3>一天跑一次就够，但要跑在月底之前</h3>
 * 默认提前 2 个月。只要任务连续两个月没跑成，才会出现「数据落进 pmax」——
 * 而那时补上分区仍然能自愈（重组会把 pmax 里的行搬回正确的分区）。
 * 提前 1 个月的话容错只有一个月，提前太多又会凭空多出一堆空分区。
 *
 * <h3>⚠️ 它能删数据，所以默认值偏保守</h3>
 * 单次最多删 3 个分区。保留期被误配成 1 时，一次只能删掉三个月，而不是把几年的数据一口气抹掉；
 * 每次这么拦下来都会 WARN。按天跑的话，正常补删完全跟得上。
 *
 * <p>幂等：建分区前先看有没有，删分区按上界算 —— 重复跑第二遍什么都不做。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "partitionMaintain",
        title = "【基础】按月分区维护",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 600,
        params = {
                @JobParam(key = "tables", desc = "要维护的表，逗号分隔",
                        defaultValue = PartitionMaintainJob.DEFAULT_TABLES),
                @JobParam(key = "aheadMonths", desc = "提前建几个月，默认 2",
                        type = JobParam.Type.INT, defaultValue = "2"),
                @JobParam(key = "retainMonths", desc = "保留几个月，默认 6",
                        type = JobParam.Type.INT, defaultValue = "6"),
                @JobParam(key = "maxDrop", desc = "单次最多删几个分区，默认 3",
                        type = JobParam.Type.INT, defaultValue = "3"),
                @JobParam(key = "dryRun", desc = "试运行：只报告要建/要删哪些分区，不动结构",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class PartitionMaintainJob implements SolvelaJob {

    /**
     * 默认维护这三张。
     *
     * <p>🔴 新增按月分区的表时<b>要把它加进来</b>（或者在后台的任务参数里加）。
     * 漏了不会报错 —— 只是那张表到了预设分区的尽头之后，数据开始悄悄堆进 {@code pmax}。
     */
    static final String DEFAULT_TABLES = "t_member_login_log,t_member_risk_event,t_member_risk_hit";

    /** 🔴 保留期不接受 0：那意味着连当月的分区都算过期 */
    private static final int MIN_RETAIN_MONTHS = 1;

    private final PartitionMaintainService service;

    @Override
    public String execute(SolvelaJobContext ctx) {
        // ⚠️ 日期取数据库时钟：JVM 与库差一天的话，补的分区和删的分区都会错一个月
        LocalDate today = ctx.dbNow().toLocalDate();
        int ahead = Math.max(0, ctx.intParam("aheadMonths", 2));
        int retain = Math.max(MIN_RETAIN_MONTHS, ctx.intParam("retainMonths", 6));
        int maxDrop = Math.max(0, ctx.intParam("maxDrop", 3));
        boolean dryRun = ctx.boolParam("dryRun", false);

        List<String> tables = Arrays.stream(ctx.stringParam("tables", DEFAULT_TABLES).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> report = new ArrayList<>();
        int added = 0;
        int dropped = 0;
        int skipped = 0;
        for (String table : tables) {
            ctx.checkCancelled();
            PartitionMaintainService.Outcome outcome;
            try {
                outcome = service.maintain(table, ahead, retain, maxDrop, today, dryRun);
            } catch (Exception e) {
                /*
                 * 🔴 一张表失败不影响其它表。分区操作是 DDL，失败的原因通常是这一张表的状态
                 * （锁等待、磁盘、被人手工改过），而不是任务本身 —— 让它把剩下两张做完，
                 * 比整个任务红掉、三张表都不维护要好。
                 */
                log.error("【分区维护】{} 维护失败，继续处理其余表", table, e);
                report.add(table + ": 失败(" + e.getClass().getSimpleName() + ")");
                skipped++;
                continue;
            }
            if (outcome.skipped()) {
                skipped++;
                report.add(table + ": 跳过(" + outcome.skippedReason() + ")");
                continue;
            }
            added += outcome.added().size();
            dropped += outcome.dropped().size();
            if (outcome.changed()) {
                report.add(table + ": 建 " + outcome.added() + " 删 " + outcome.dropped());
            }
        }

        if (report.isEmpty()) {
            return "分区齐备，无需改动（提前 " + ahead + " 个月，保留 " + retain + " 个月）";
        }
        return (dryRun ? "试运行：" : "")
                + "建 " + added + " 个、删 " + dropped + " 个分区"
                + (skipped > 0 ? "，跳过 " + skipped + " 张表" : "")
                + " —— " + String.join("；", report);
    }
}
