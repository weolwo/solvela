package net.lab1024.sa.lottery.prizerule.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票奖励配置 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */

@Data
public class LotteryPrizeRuleUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "匹配模式，0,前匹配，1后匹配", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "匹配模式，0,前匹配，1后匹配 不能为空")
    private Integer patternMode;

    @Schema(description = "奖励明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖励明细 不能为空")
    private String prizeDetails;

    @Schema(description = "开奖个数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开奖个数 不能为空")
    private Integer winCount;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}