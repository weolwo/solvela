package solvela.consumer.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.consumer.handler.PrizeDispatchHandler;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeProposalStatusEnum;
import solvela.prize.PrizeLog;
import solvela.prize.prizelog.dao.PrizeLogDao;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发奖对账：把卡在半路的奖捞回来。
 *
 * <h3>拆成两个服务之后，奖会卡在两个地方</h3>
 * <pre>
 *   marketing 落发奖流水 ──同步 HTTP──▶ member 生成提案 ──异步消息──▶ marketing 落终态
 *              ↑ ①卡在这                                    ↑ ②卡在这
 * </pre>
 * <ul>
 *   <li><b>① proposal_status = PENDING</b>：同步调用没发出去或没回来
 *       （进程挂了、网络断了、下游超时）。<b>本任务会重投</b>；</li>
 *   <li><b>② ACCEPTED 但 status 还是等待</b>：提案生成了，但入账结果那条消息没回来
 *       （消息丢了），<b>或者提案还在人工审批池里</b>。</li>
 * </ul>
 *
 * <h3>🔴 ② 只报不改</h3>
 * 因为「消息丢了」和「还在审批」<b>从这一侧看长得一模一样</b>。
 * 自动判成失败会把一笔正在等审批的奖标死；自动判成成功更糟 ——
 * 那正是「记录显示成功、用户其实没收到」。所以只统计、只告警，由人去看。
 *
 * <p>真要自动化，需要的是<b>反查提案状态的接口</b>，而不是在这里猜。
 *
 * <h3>重投是安全的</h3>
 * 会员服务按 {@code sourceBizId} 判重，重复请求返回与第一次相同的结果、不重复发奖。
 * 这条幂等保证写在 {@code MemberProposalApi} 的契约里 —— 本任务是它的第一个真实用户。
 *
 * <p>⚠️ 只捞<b>足够旧</b>的行（默认 10 分钟前）：刚落库的那条可能正在被同步调用处理中，
 * 捞它等于和自己抢。这个延迟不是保守，是并发正确性。
 *
 * @Date 2026-08-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "prizeDispatchReconcile",
        title = "【发奖】跨服务对账与重投",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 600,
        params = {
                @JobParam(key = "delayMinutes", desc = "只处理多少分钟前的记录，默认 10",
                        type = JobParam.Type.INT, defaultValue = "10"),
                @JobParam(key = "limit", desc = "单次最多重投多少条，默认 200",
                        type = JobParam.Type.INT, defaultValue = "200"),
                @JobParam(key = "staleHours", desc = "「已受理但无终态」超过多少小时算异常，默认 24",
                        type = JobParam.Type.INT, defaultValue = "24"),
                @JobParam(key = "dryRun", desc = "试运行：只统计，不重投",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class PrizeDispatchReconcileJob implements SolvelaJob {

    private final PrizeLogDao prizeLogDao;

    private final PrizeDispatchHandler prizeDispatchHandler;

    @Override
    public String execute(SolvelaJobContext ctx) {
        LocalDateTime now = ctx.dbNow();
        LocalDateTime before = now.minusMinutes(ctx.intParam("delayMinutes", 10));
        int limit = ctx.intParam("limit", 200);

        List<PrizeLog> pending = prizeLogDao.selectList(Wrappers.<PrizeLog>lambdaQuery()
                .eq(PrizeLog::getProposalStatus, PrizeProposalStatusEnum.PENDING)
                .lt(PrizeLog::getCreateTime, before)
                .orderByAsc(PrizeLog::getId)
                .last("limit " + limit));

        long stale = prizeLogDao.selectCount(Wrappers.<PrizeLog>lambdaQuery()
                .eq(PrizeLog::getProposalStatus, PrizeProposalStatusEnum.ACCEPTED)
                .eq(PrizeLog::getStatus, PrizeDispatchStatusEnum.WAITING)
                .lt(PrizeLog::getCreateTime, now.minusHours(ctx.intParam("staleHours", 24))));

        if (stale > 0) {
            // 只报不改，理由见类注释。这一行就是告警信号
            log.warn("【发奖对账】有 {} 笔已受理但迟迟没有终态 —— 可能是入账结果消息丢了，"
                    + "也可能还在人工审批池里。需要人工比对提案状态", stale);
        }

        if (ctx.boolParam("dryRun", false)) {
            return String.format("试运行：待重投 %d 笔，已受理但无终态 %d 笔，本次未改动任何数据",
                    pending.size(), stale);
        }

        int retried = 0;
        for (PrizeLog prizeLog : pending) {
            // 超时靠中断实现，每条开头自查一次
            ctx.checkCancelled();
            try {
                // 复用发货入口：它本来就是 public 的（后台审批通过后也调它），
                // 状态怎么落、被拒时原因写哪里，全部与正常链路完全一致 ——
                // 对账任务自己再写一遍状态处理，是漂移的开始
                prizeDispatchHandler.doDispatch(prizeLog);
                retried++;
            } catch (Exception e) {
                // 一条失败不能拖垮整批：剩下的还得继续捞
                log.error("【发奖对账】重投失败，LogId: {}", prizeLog.getId(), e);
            }
        }

        String result = String.format("重投 %d 笔；已受理但无终态 %d 笔（只报不改）", retried, stale);
        log.info("【发奖对账】{}", result);
        return result;
    }
}
