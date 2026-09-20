package solvela.member.grade.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.member.MemberGrowth;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.service.MemberGradeSettleService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员等级期末结算 —— 到期的判一次，缓冲期满的下终局。
 *
 * <h3>🔴 「千人千面」周期，所以每天都有人到期</h3>
 * 周期是<b>各人自己的 12 个月</b>（从第一次成长值入账起算），不是自然年。
 * 好处是全平台不会在同一天结算；代价是<b>这个 job 每天都有活干，没有安全窗口</b>，
 * 它得天天正确。
 *
 * <p>所以绝不能写成「一个大事务 {@code SELECT ... WHERE period_end < NOW()} 然后循环更新」：
 * 一个人算错就整批回滚，而下一轮还会踩同一个人 —— 那是活锁。
 * 形状照抄 {@code MallOrderExpireJob}：分页捞、单人独立事务、单人失败只影响自己。
 *
 * <h3>为什么 {@code idempotent = true}</h3>
 * 结清那一步带 {@code AND period_end = 旧值} 的条件更新，周期快照上有
 * {@code uk(member_id, period_no)} —— 重复跑第二遍影响 0 行、整人跳过。
 *
 * <h3>⚠️ 一天跑几次，不是一天跑一次</h3>
 * 每小时一轮。周期到点到真正结算之间的延迟，用户是看得见的（他的等级页上
 * 「本期截止」已经过了却还没动）。一天一次意味着最多晚 24 小时才有反应。
 *
 * @author alaric
 * @date 2026-09-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "memberGradeSettle",
        title = "【会员】等级期末结算与保级判定",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 900,
        params = {
                @JobParam(key = "batchSize", desc = "单批处理多少人，默认 100",
                        type = JobParam.Type.INT, defaultValue = "100"),
                @JobParam(key = "maxRound", desc = "单次执行最多几批，默认 50",
                        type = JobParam.Type.INT, defaultValue = "50"),
                @JobParam(key = "dryRun", desc = "试运行：只统计待结算人数，不动数据",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class MemberGradeSettleJob implements SolvelaJob {

    private final MemberGrowthDao memberGrowthDao;
    private final MemberGradeSettleService memberGradeSettleService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        /*
         * ⚠️ 时间取 dbNow 而不是 LocalDateTime.now()：周期边界与缓冲期是否到点全靠它判。
         * 多实例部署时各节点 JVM 时钟漂移几分钟，就会出现「A 节点认为到期了、B 节点认为没到」。
         */
        LocalDateTime now = ctx.dbNow();
        int batchSize = ctx.intParam("batchSize", 100);
        int maxRound = ctx.intParam("maxRound", 50);

        if (ctx.boolParam("dryRun", false)) {
            List<MemberGrowth> preview = memberGrowthDao.selectDueForSettle(now, batchSize);
            log.info("【等级结算】试运行：截至 {} 至少有 {} 人待结算", now, preview.size());
            return "试运行：至少 " + preview.size() + " 人待结算（单批上限 " + batchSize
                    + "），本次未改动任何数据";
        }

        int settled = 0;
        int skipped = 0;
        int failed = 0;
        int protectStarted = 0;
        int downgraded = 0;

        for (int round = 0; round < maxRound; round++) {
            ctx.checkCancelled();

            /*
             * 每轮都重新捞：上一轮结清的人周期已经推进、进入缓冲的人 protect_until 已经不为空，
             * 都不会再被捞到。所以这里不需要 offset —— 用 offset 反而会跳过
             * 刚被别的节点改动的行。
             */
            List<MemberGrowth> batch = memberGrowthDao.selectDueForSettle(now, batchSize);
            if (batch.isEmpty()) {
                break;
            }

            for (MemberGrowth growth : batch) {
                try {
                    String result = memberGradeSettleService.settle(growth, now);
                    if (result == null) {
                        skipped++;
                        continue;
                    }
                    settled++;
                    if ("PROTECT_START".equals(result)) {
                        protectStarted++;
                    } else if ("PROTECT_FAILED".equals(result)) {
                        downgraded++;
                    }
                } catch (Exception e) {
                    // 单人独立事务，一个人失败不影响别人。但必须计数 ——
                    // 否则「全部失败」和「全部成功」在返回值上长得一样
                    failed++;
                    log.error("【等级结算】memberId={} 结算失败，本人已回滚，下轮重试",
                            growth.getMemberId(), e);
                }
            }

            if (batch.size() < batchSize) {
                break;
            }
            // 🔴 整批都失败时必须退出，否则同一批会被无限重捞 —— 那是个活锁
            if (failed >= batch.size()) {
                log.error("【等级结算】整批 {} 人全部失败，停止本次执行", batch.size());
                break;
            }
        }

        String result = String.format("结算 %d 人（其中进入保级缓冲 %d、降级 %d），跳过 %d，失败 %d",
                settled, protectStarted, downgraded, skipped, failed);
        log.info("【等级结算】{}", result);
        return result;
    }
}
