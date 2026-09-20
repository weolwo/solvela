package solvela.member.grade.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.domain.dto.GrowthCheckRow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 成长值对账 —— 方案 §5.2 欠的那一笔。
 *
 * <pre>
 *   t_member_growth.current_period_value  ?=  SUM(t_member_growth_log.delta WHERE period_tag=本期)
 * </pre>
 *
 * <h3>🔴 为什么这张冗余非对账不可</h3>
 * 判级是热路径，每次扫流水不现实，所以 {@code current_period_value} 是必须的冗余。
 * 而冗余一旦不对账，迟早会出现「明细加起来不等于总数」——
 * <b>到那时已经没人知道该信哪个</b>。等级、保级、降级全挂在这个数上。
 *
 * <h3>🔴 流水是真相，主表是派生</h3>
 * 流水 append-only 且带幂等唯一键 {@code (source, biz_id)}；主表是一条
 * {@code SET x = x + ?} 累出来的。两者不一致时，<b>一定是主表错</b>。
 *
 * <h3>默认只报不改</h3>
 * 虽然「谁对」是确定的，但默认不自动修 —— 自动修会让引起漂移的那个 bug
 * 每半小时被悄悄抹平一次，<b>没有人会知道它存在</b>。
 * 与 {@code PrizeDispatchReconcileJob} 对「已受理无终态」的处理是同一条：只报不改。
 *
 * <p>真要修时把 {@code autoFix} 打开 —— 那是一次<b>显式的人为动作</b>，
 * 而不是一个天天在跑的静默行为。
 *
 * @author alaric
 * @date 2026-09-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberGrowthReconcileService {

    private final MemberGrowthDao memberGrowthDao;

    /**
     * 对账结果。
     *
     * <p>{@code scanned} 也要报：只报「发现几笔异常」的话，
     * 「一切正常」和「压根没扫到人」在输出上<b>长得一模一样</b> ——
     * 而后者才是真正该报警的那种（游标写错、时间窗写错、库连错）。
     */
    @Getter
    public static class ReconcileResult {
        private int scanned;
        private int drifted;
        private int fixed;
        private int skipped;
        private long orphanLogMembers;
        private final List<String> samples = new ArrayList<>();

        /** 最多留几条样本进日志。全量打印会在真出事时刷爆日志，而那时最需要日志可读 */
        private static final int MAX_SAMPLE = 20;

        void addSample(GrowthCheckRow row) {
            if (samples.size() < MAX_SAMPLE) {
                samples.add(String.format("member=%d period=%s 主表=%d 流水=%d 差=%+d",
                        row.getMemberId(), row.getPeriodTag(),
                        row.getRecorded(), row.getLogSum(), row.diff()));
            }
        }

        public String summary() {
            return String.format("扫描 %d 人，对不上 %d 人（已校正 %d，跳过 %d），孤儿流水会员 %d",
                    scanned, drifted, fixed, skipped, orphanLogMembers);
        }
    }

    /**
     * 跑一轮对账。
     *
     * @param changedSince  只看这个时刻之后有过变动的人；{@code null} = 全量扫
     * @param batchSize     单批多少人
     * @param maxRound      最多扫几批
     * @param autoFix       true 则把主表校正成流水求和
     * @param cancelCheck   每批开头调一次，用于响应任务超时/取消
     */
    public ReconcileResult reconcile(LocalDateTime changedSince, int batchSize, int maxRound,
                                     boolean autoFix, Runnable cancelCheck) {
        ReconcileResult result = new ReconcileResult();
        result.orphanLogMembers = memberGrowthDao.countOrphanGrowthLog();
        if (result.orphanLogMembers > 0) {
            /*
             * 🔴 这一条比「值对不上」严重得多：有流水却没有主表行，说明有人绕过
             *    MemberGrowthService.accrue 直接写了流水 —— 对账连比都没法比。
             */
            log.error("【成长值对账】有 {} 个会员只有流水、没有成长值主表行 —— "
                    + "说明有人绕过 accrue 直接写流水，请查代码", result.orphanLogMembers);
        }

        long cursor = 0L;
        for (int round = 0; round < maxRound; round++) {
            if (cancelCheck != null) {
                cancelCheck.run();
            }
            List<GrowthCheckRow> batch =
                    memberGrowthDao.selectForReconcile(changedSince, cursor, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            for (GrowthCheckRow row : batch) {
                result.scanned++;
                // 游标按【扫到的】最大 id 推，不是按异常行 —— 一批全对也要往前走
                cursor = Math.max(cursor, row.getMemberId());
                if (!row.drifted()) {
                    continue;
                }
                result.drifted++;
                result.addSample(row);
                if (autoFix) {
                    fix(row, result);
                }
            }
            if (batch.size() < batchSize) {
                break;
            }
        }

        if (result.drifted > 0) {
            // 这一行就是告警信号。样本带上，免得还要再去库里捞一遍才知道差在哪
            log.error("【成长值对账】{} —— 样本：{}", result.summary(), result.samples);
        } else {
            log.info("【成长值对账】{}", result.summary());
        }
        return result;
    }

    private void fix(GrowthCheckRow row, ReconcileResult result) {
        int changed = memberGrowthDao.correctPeriodValue(
                row.getMemberId(), row.getRecorded(), row.getLogSum());
        if (changed == 0) {
            /*
             * 影响 0 行 = 从读到写之间这个人又入账了，我们手里的 logSum 已经过期。
             * 放弃这一个，下一轮再对 —— 强写会把那笔新入账覆盖掉，
             * 等于对账任务自己制造一笔差异，而且是往少了写。
             */
            result.skipped++;
            log.warn("【成长值对账】校正时值已被改动，本次跳过。memberId={}", row.getMemberId());
            return;
        }
        result.fixed++;
        log.warn("【成长值对账】已校正 memberId={}，{} → {}（差 {}）",
                row.getMemberId(), row.getRecorded(), row.getLogSum(), row.diff());
    }
}
