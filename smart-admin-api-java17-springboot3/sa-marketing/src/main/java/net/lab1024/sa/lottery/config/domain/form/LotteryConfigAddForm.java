package net.lab1024.sa.lottery.config.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票配置 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */

@Data
public class LotteryConfigAddForm {

    @Schema(description = "租户id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户id 不能为空")
    private String tenantId;

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    @Schema(description = "彩票编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    private String lotteryCode;

    @Schema(description = "彩票名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票名称 不能为空")
    private String lotteryName;

    @Schema(description = "字符集：0-9, A-Z", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字符集：0-9, A-Z 不能为空")
    private String numberCharset;

    @Schema(description = "号码长度", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "号码长度 不能为空")
    private Integer numberLength;

    @Schema(description = "号池总数 (如: 1,000,000)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "号池总数 (如: 1,000,000) 不能为空")
    private Integer totalCount;

    @Schema(description = "状态：0-下线, 1-上线")
    private Integer status;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}