package solvela.admin.module.system.job.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 触发时间预览：在任务<b>保存之前</b>算出接下来几次会在什么时候跑。
 *
 * @author alaric
 * @date 2026-08-12
 */
@Data
public class SolvelaJobTriggerPreviewForm {

    /**
     * 任务 id。<b>编辑已有任务时传，新建时不传。</b>
     *
     * <p>🔴 它决定预览能不能算准：打散偏移是 {@code hash(jobId) % jitterSeconds}，
     * 新建任务还没有 id，也就还不知道会被打散多少秒 —— 那时只能给出「未打散的基准时刻」
     * 并如实告诉用户存在一个 0~N 秒的固定偏移。<b>不能假装算准了。</b>
     */
    @Schema(description = "任务id|编辑时传，新建不传")
    private Integer jobId;

    @Schema(description = "触发类型：cron / one_time")
    @NotBlank(message = "触发类型不能为空")
    private String triggerType;

    @Schema(description = "触发配置：cron 表达式，或一次性任务的时刻")
    @NotBlank(message = "触发配置不能为空")
    private String triggerValue;

    @Schema(description = "预设档位，用于推导打散秒数")
    private String presetCode;

    @Schema(description = "打散秒数，仅 CUSTOM 档位需要传")
    private Integer jitterSeconds;
}
