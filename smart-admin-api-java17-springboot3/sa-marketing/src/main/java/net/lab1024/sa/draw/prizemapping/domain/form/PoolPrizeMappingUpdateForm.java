package net.lab1024.sa.draw.prizemapping.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池奖项映射 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */

@Data
public class PoolPrizeMappingUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "奖池编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池编码 不能为空")
    private String poolCode;

    @Schema(description = "奖项id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "奖项id 不能为空")
    private Long prizeItemId;

    @Schema(description = "中奖概率(万分位)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "中奖概率(万分位) 不能为空")
    private BigDecimal probability;

    @Schema(description = "是否兜底奖项：1-兜底，每池最多一个")
    private Integer isFallback;

    @Schema(description = "序号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "序号 不能为空")
    private Integer sortWeight;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}