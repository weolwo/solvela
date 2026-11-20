package solvela.member.risk.security;

import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;

/**
 * 改名前的任务名 {@code securityIncidentDrain}，原样转给 {@link SecurityEventDrainJob}。
 *
 * <h3>🔴 为什么非有它不可</h3>
 * 调度器按 {@code t_solvela_job.handler_name} 找处理器，找不到就把那一行标成 {@code handler_missing_flag = 1}，
 * 而扫描只挑 {@code handler_missing_flag = 0} 的行 —— <b>标上之后，处理器后来出现了也不会自己恢复</b>。
 * 改名要分两步落地（先发版、再跑脚本把那一行改成新名字），中间无论库里是新名还是旧名，都必须有处理器认它；
 * 否则安全事件落库任务会永久静默停掉：处罚照常生效，但不再自动拉黑拉灰、事件也不再落库。
 *
 * <p>同一时刻库里只有一行（不是旧名就是新名），所以不会跑两遍。
 *
 * <p>TEMP(安全事件改名-收口): 移除条件 —— 改名的第二步脚本已在所有环境执行（{@code t_solvela_job} 里没有
 * {@code handler_name = 'securityIncidentDrain'} 的行），见 docs/架构/安全事件改名-实施方案.md。
 */
@Component
@SolvelaJobHandler(
        name = "securityIncidentDrain",
        title = "【安全】安全事件落库（改名过渡，勿用它新建任务）",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.FAST,
        idempotent = false,
        defaultTimeoutSeconds = 30
)
public class LegacySecurityIncidentDrainJob implements SolvelaJob {

    private final SecurityEventDrainJob delegate;

    public LegacySecurityIncidentDrainJob(SecurityEventDrainJob delegate) {
        this.delegate = delegate;
    }

    @Override
    public String execute(SolvelaJobContext ctx) {
        return delegate.execute(ctx);
    }
}
