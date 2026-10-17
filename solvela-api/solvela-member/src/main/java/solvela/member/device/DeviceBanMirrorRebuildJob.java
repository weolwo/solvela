package solvela.member.device;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;

/**
 * 每 5 分钟按 {@code t_device.status} 全量重建一次封禁镜像（{@code sec:dev:banned}）。
 *
 * <h3>它是自愈，不是主路径</h3>
 * 主路径是人工处置时立即同步（{@link DeviceDispositionService#disposeManually}）。
 * 这个任务兜住的是：同步那一下 Redis 恰好写不进去、Redis 被清空或被内存淘汰、有人绕过后台直接改了库。
 *
 * <p>🔴 镜像丢了的后果是<b>静默</b>的：网关查不到这台设备，它就照常能抽奖、签到、登录。
 *
 * @Date 2026-10-17
 */
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "deviceBanMirrorRebuild",
        title = "【安全】设备封禁镜像重建",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.FAST,
        idempotent = true,
        defaultTimeoutSeconds = 30
)
public class DeviceBanMirrorRebuildJob implements SolvelaJob {

    private final DeviceDispositionService dispositionService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        return "镜像里有 " + dispositionService.rebuildBannedMirror() + " 台被封禁的设备";
    }
}
