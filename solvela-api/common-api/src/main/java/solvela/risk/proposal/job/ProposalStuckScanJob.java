package solvela.risk.proposal.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.support.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.support.jobspi.core.JobParam;
import solvela.base.module.support.jobspi.core.SolvelaJob;
import solvela.base.module.support.jobspi.core.SolvelaJobContext;
import solvela.base.module.support.jobspi.core.SolvelaJobHandler;
import solvela.risk.proposal.dao.ProposalRecordDao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 提案卡单扫描：把「钱卡在半路」的提案定时捞出来记一笔。
 *
 * <h3>它为什么只报不修</h3>
 * 下发是在提案事务提交后<b>同步</b>调起的，进程中途退出就没有第二次机会，
 * 而工程里没有任何补偿 —— 提案会一直停在 30-待执行 / 40-执行中，
 * 钱既没发出去，也没被标成失败。这个任务负责让这件事<b>被看见</b>。
 *
 * <p>🔴 <b>它刻意不自动重发。</b> 因为从提案表看不出上一次下发到底走到哪一步：
 * 40-执行中 可能是"根本没开始"，也可能是"发了一半"（60-部分成功 这个状态的存在
 * 本身就说明部分到账是真实发生过的）。在分不清这两者的情况下自动重发，
 * 等于按概率给用户<b>发两次钱</b> —— 而多发出去的钱是收不回来的。
 *
 * <p>要做到能自动重发，前置条件是资产下发侧有一个真正的幂等键
 * （像 {@code t_prize_log.uk_external_biz} 那样，重放第二次会被唯一索引挡掉）。
 * 那是另一件事，不该由一个扫描任务顺手带过。
 *
 * <h3>那它到底有什么用</h3>
 * 三件事：把卡单数量留成一条<b>带时间戳的记录</b>（提案漏斗只能看到"此刻"）、
 * 在日志里打出具体单号让人能直接去查、以及在数量突破阈值时用 WARN 级别喊出来。
 * 在没有幂等键之前，"有人知道"就是这里能提供的全部价值，
 * 而它已经比"没人知道"好很多。
 *
 * @author alaric
 * @date 2026-08-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "proposalStuckScan",
        title = "【风控】提案卡单扫描（只报不修）",
        group = "OPS",
        lane = SolvelaJobLaneEnum.FAST,
        idempotent = true,
        defaultTimeoutSeconds = 30,
        params = {
                @JobParam(key = "stuckMinutes", desc = "多久没动过算卡单（分钟）",
                        type = JobParam.Type.INT, defaultValue = "30"),
                @JobParam(key = "warnThreshold", desc = "超过这个条数用 WARN 级别喊，0 表示只要有就喊",
                        type = JobParam.Type.INT, defaultValue = "0")
        }
)
public class ProposalStuckScanJob implements SolvelaJob {

    /**
     * 日志里最多打几条单号。打全量没有意义 —— 真卡了几千条，前十条和后十条是同一个原因，
     * 而把几千个单号刷进日志只会让真正有用的那行被冲掉。
     */
    private static final int SAMPLE_LIMIT = 10;

    private final ProposalRecordDao proposalRecordDao;

    @Override
    public String execute(SolvelaJobContext ctx) {
        LocalDateTime now = ctx.dbNow();
        int stuckMinutes = ctx.intParam("stuckMinutes", 30);
        int warnThreshold = ctx.intParam("warnThreshold", 0);

        long stuckCount = proposalRecordDao.countStuckDispatch(now, stuckMinutes);
        if (stuckCount == 0) {
            return "没有卡在下发的提案";
        }

        List<Map<String, Object>> samples =
                proposalRecordDao.selectStuckDispatchSample(now, stuckMinutes, SAMPLE_LIMIT);
        StringBuilder detail = new StringBuilder();
        for (Map<String, Object> row : samples) {
            detail.append(row.get("tradeNo"))
                    .append("(status=").append(row.get("status"))
                    .append(", 卡了").append(row.get("stuckMinutes")).append("分钟) ");
        }

        String message = "有 " + stuckCount + " 条提案卡在下发超过 " + stuckMinutes
                + " 分钟：钱既没发出去也没标成失败。前 " + samples.size() + " 条：" + detail;
        if (stuckCount > warnThreshold) {
            // WARN 而不是 ERROR：任务本身是成功的，卡单是业务状态，不该让任务被判失败后触发重试
            log.warn("【提案卡单】{}", message);
        } else {
            log.info("【提案卡单】{}", message);
        }

        /*
         * 刻意不在这里自动重发：从提案表分不出「根本没开始下发」和「发了一半」
         * （60-部分成功 的存在说明后者真实发生过），分不清就重发等于按概率发两次钱。
         * 要能自动重发，得先在资产下发侧有一个真正的幂等键。
         */
        return message;
    }
}
