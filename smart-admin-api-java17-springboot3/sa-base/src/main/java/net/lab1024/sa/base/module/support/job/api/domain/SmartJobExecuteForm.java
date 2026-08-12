package net.lab1024.sa.base.module.support.job.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 定时任务-手动执行
 *
 * @author huke
 * @date 2024/6/18 20:30
 */
@Data
public class SmartJobExecuteForm {

    @Schema(description = "任务id")
    @NotNull(message = "任务id不能为空")
    private Integer jobId;

    @Schema(description = "定时任务参数|可选")
    @Length(max = 2000, message = "定时任务参数最多2000字符")
    private String param;

    /**
     * 业务日期。不传则按执行器声明的 bizDateOffset 推导。
     *
     * <p>🔴 这是数据类任务的运营刚需：「昨天的统计跑错了，重跑 8 月 9 号那天」——
     * 没有这个字段根本表达不了，只能改系统时间或手改数据。
     */
    @Schema(description = "业务日期|可选，用于补跑历史日期")
    private java.time.LocalDate bizDate;

    @Schema(hidden = true)
    private String updateName;
}
