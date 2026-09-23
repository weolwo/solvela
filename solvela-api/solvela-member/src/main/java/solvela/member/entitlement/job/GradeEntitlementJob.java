package solvela.member.entitlement.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.member.entitlement.service.GradeEntitlementGrantService;

import java.time.LocalDateTime;

/**
 * 每天生成「待领取」的等级权益，并把到期未领的置为过期。
 *
 * <h3>🔴 每天扫全量，而不是「月度券只在 1 号扫」</h3>
 * 只在 1 号扫的话，那天 job 没跑起来（部署、故障、机器睡着了）就是
 * <b>整整一个月的权益没发</b>，而且要等到下个月才有下一次机会。
 * 每天扫 + 唯一键幂等（{@code uk(member_id, entitlement_id, period_key)}），
 * 等于<b>自带补跑</b>：昨天生成过的今天插不进去，代价只是一次失败的 INSERT。
 *
 * <h3>⚠️ 时间取 {@link SolvelaJobContext#dbNow()}，不是 JVM 时钟</h3>
 * 周期键（{@code yyyy} / {@code yyyyMM}）是幂等键的一部分。
 * 多实例部署下各节点 JVM 时钟未必一致，跨零点/跨月那一刻
 * A 节点算出 202609、B 算出 202610 —— 同一个人会拿到<b>两份</b>月度券，
 * 而唯一键拦不住它（周期键本来就不一样）。
 *
 * <h3>为什么生成与过期放在同一个 job 里</h3>
 * 它们是同一件事的两半：今天该发的发出去，该收的收回来。
 * 拆成两个 job 的唯一后果是「其中一个被停用了而没人发现」——
 * 而只有过期那半停了的话，待领取会无声地一直堆。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "gradeEntitlementGrant",
        title = "【会员】等级权益生成与过期（生日礼/月度券）",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 1800
)
public class GradeEntitlementJob implements SolvelaJob {

    private final GradeEntitlementGrantService gradeEntitlementGrantService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        LocalDateTime now = ctx.dbNow();

        GradeEntitlementGrantService.GenerateResult result =
                gradeEntitlementGrantService.generate(now.toLocalDate(), ctx::checkCancelled);

        /*
         * 过期放在生成【之后】：先把今天该给的给出去，再收该收的。
         * 反过来的话，一份今天生成、claimDays=0 的配置会在同一轮里被自己置过期 ——
         * 那是配置错误，但用户看到的是「刚出现就没了」。
         */
        int expired = gradeEntitlementGrantService.expire(now);

        String summary = result.summary() + "，本轮过期 " + expired + " 条";
        log.info("【权益】{}", summary);
        return summary;
    }
}
