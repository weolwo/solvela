package net.lab1024.sa.prize.prizeconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 奖品配置表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */

@Data
public class PrizeConfigAddForm {

    @Schema(description = "租户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户ID 不能为空")
    private String tenantId;

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    @Schema(description = "优惠配置ID")
    private Long promotionConfigId;

    @Schema(description = "资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM")
    private String prizeType;

    @Schema(description = "奖品名称")
    private String prizeName;

    @Schema(description = "奖品编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖品编码 不能为空")
    private String prizeCode;

    @Schema(description = "奖品级别")
    private Integer prizeLevel;

    @Schema(description = "奖励价值")
    private BigDecimal prizeValue;

    @Schema(description = "排序权重")
    private Integer sortWeight;

    @Schema(description = "扩展信息：如奖品图片URL、跳转链接等")
    private String ext;

}