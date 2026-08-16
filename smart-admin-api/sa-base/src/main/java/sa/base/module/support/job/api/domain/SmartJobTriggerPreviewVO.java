package sa.base.module.support.job.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 触发时间预览结果。
 *
 * <p>存在的意义有两条，第二条比第一条更要紧：
 * <ol>
 *   <li>cron 写错是<b>静默失败</b>：{@code 0 0 0 1 * *} 到底是「每月 1 号」还是「每天 1 点」，
 *       看五次预览两秒就知道，而不看的话要等任务该跑没跑才发现；</li>
 *   <li>🔴 <b>打散（jitter）默认开启且原本不可见</b> —— 运营配「每天 2:00」，
 *       实际会在 {@code 02:00:37} 触发（LIGHT 档打散 30 秒、NORMAL 档 60 秒）。
 *       没有这个预览，那个 37 秒会被当成 bug 报上来。</li>
 * </ol>
 *
 * @author alaric
 * @date 2026-08-12
 */
@Data
public class SmartJobTriggerPreviewVO {

    @Schema(description = "触发配置是否合法")
    private Boolean valid;

    @Schema(description = "不合法时的人话原因；合法时为补充说明（如打散提示）")
    private String message;

    @Schema(description = "实际生效的打散秒数，0 表示不打散")
    private Integer jitterSeconds;

    /**
     * 预览的时刻是否<b>精确</b>。
     *
     * <p>{@code false} 表示这是「未打散的基准时刻」，实际触发会在其上固定延后
     * 0~{@link #jitterSeconds} 秒 —— 新建任务时必然如此，因为偏移量由 jobId 决定，
     * 而 id 要等保存后才有。<b>如实标注，不假装算准。</b>
     */
    @Schema(description = "预览时刻是否精确：新建任务时为 false（打散量待定）")
    private Boolean exact;

    @Schema(description = "接下来 N 次的触发时刻")
    private List<LocalDateTime> nextTimeList;
}
