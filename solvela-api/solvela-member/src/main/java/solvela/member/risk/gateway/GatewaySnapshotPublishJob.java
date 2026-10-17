package solvela.member.risk.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;

/**
 * 每 5 分钟把网关规则快照重发一次。
 *
 * <h3>它是自愈，不是主路径</h3>
 * 主路径是「后台保存之后立即发布」。这个任务兜住的是主路径之外的情况：
 * 保存时 Redis 恰好写不进去、Redis 被清空或开了会淘汰键的内存策略、有人绕过后台直接改了库。
 * 内容没变时版本不变，网关不会因为它白白重载。
 *
 * <p>🔴 网关读不到快照时用的是内置默认值、读到过一次之后用的是上一份 —— 都不会「不限流」；
 * 但后台改的阈值要等这个任务（或下一次保存）才能真正到达网关。
 *
 * @Date 2026-10-17
 */
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "gatewaySnapshotPublish",
        title = "【安全】网关规则快照重发",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.FAST,
        idempotent = true,
        defaultTimeoutSeconds = 30
)
public class GatewaySnapshotPublishJob implements SolvelaJob {

    private final GatewaySnapshotPublisher publisher;

    @Override
    public String execute(SolvelaJobContext ctx) {
        GatewaySnapshotPublisher.Outcome outcome = publisher.publish();
        return String.format("版本 %s，规则 %d 条，IP 名单 %d 条，%s", outcome.version(), outcome.rules(),
                outcome.ipEntries(), outcome.written() ? "已写入" : "Redis 里已有更新的，未覆盖");
    }
}
