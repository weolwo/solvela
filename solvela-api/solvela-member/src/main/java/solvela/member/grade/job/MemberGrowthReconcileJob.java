package solvela.member.grade.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.member.grade.service.MemberGrowthReconcileService;

import java.time.LocalDateTime;

/**
 * 成长值对账：主表的冗余值 vs 流水求和。
 *
 * <h3>它是方案 §5.2 的兑现</h3>
 * 原话：「冗余就要对账。冗余而不对账，迟早出现明细和总数对不上，
 * 而那时已经没人知道哪个是对的。」等级、保级、降级全挂在那个数上。
 *
 * <h3>默认只报不改</h3>
 * 谁对是确定的（流水 append-only 且带幂等唯一键，主表是累出来的派生值），
 * 但<b>默认不自动修</b> —— 自动修会让引起漂移的那个 bug
 * 每半小时被悄悄抹平一次，没有人会知道它存在。
 *
 * <p>真要修时把 {@code autoFix} 打开跑一次，那是显式的人为动作。
 *
 * <h3>⚠️ 默认只扫最近 24 小时有变动的人</h3>
 * 没动过的人不可能<b>新产生</b>漂移，全量扫一遍在会员量大了之后是纯浪费。
 * 但这也意味着<b>历史遗留的漂移它看不见</b> ——
 * 首次上线、或者这个 job 停过一段时间之后，要手工跑一次
 * {@code lookbackHours=0}（全量）把陈账清一遍。
 *
 * @author alaric
 * @date 2026-09-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "memberGrowthReconcile",
        title = "【会员】成长值对账（冗余值 vs 流水求和）",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 900,
        params = {
                @JobParam(key = "lookbackHours", desc = "只看多少小时内有变动的人；0 = 全量扫",
                        type = JobParam.Type.INT, defaultValue = "24"),
                @JobParam(key = "batchSize", desc = "单批多少人，默认 500",
                        type = JobParam.Type.INT, defaultValue = "500"),
                @JobParam(key = "maxRound", desc = "单次执行最多几批，默认 50",
                        type = JobParam.Type.INT, defaultValue = "50"),
                @JobParam(key = "autoFix", desc = "把主表校正成流水求和。⚠️ 默认关，开了就不再只报不改",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class MemberGrowthReconcileJob implements SolvelaJob {

    private final MemberGrowthReconcileService memberGrowthReconcileService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        // ⚠️ 用 dbNow 不用 JVM 时钟：时间窗判据必须和写入方（也是数据库时钟）用同一个钟
        LocalDateTime now = ctx.dbNow();
        int lookbackHours = ctx.intParam("lookbackHours", 24);
        LocalDateTime changedSince = lookbackHours <= 0 ? null : now.minusHours(lookbackHours);
        boolean autoFix = ctx.boolParam("autoFix", false);

        MemberGrowthReconcileService.ReconcileResult result =
                memberGrowthReconcileService.reconcile(
                        changedSince,
                        ctx.intParam("batchSize", 500),
                        ctx.intParam("maxRound", 50),
                        autoFix,
                        ctx::checkCancelled);

        String scope = changedSince == null ? "全量" : lookbackHours + " 小时内有变动";
        return String.format("%s：%s%s", scope, result.summary(), autoFix ? "" : "（只报不改）");
    }
}
