package net.lab1024.sa.lottery.prizerule.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票奖励配置 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */

@Data
public class LotteryPrizeRuleAddForm {

    @Schema(description = "租户id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户id 不能为空")
    private String tenantId;

    @Schema(description = "彩票编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    private String lotteryCode;

    @Schema(description = "期号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "期号 不能为空")
    private String issueNo;

    @Schema(description = "匹配模式，0,前匹配，1后匹配", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "匹配模式，0,前匹配，1后匹配 不能为空")
    private Integer patternMode;

    @Schema(description = "奖励明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖励明细 不能为空")
    private String prizeDetails;

    @Schema(description = "开奖个数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开奖个数 不能为空")
    private Integer winCount;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}