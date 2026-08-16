package sa.prize.prizeconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.Data;
import sa.base.common.util.SmartCodeUtil;

/**
 * 奖品配置表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */

@Data
public class PrizeConfigAddForm {

    @Schema(description = "租户ID，不传落库取默认值 '0'")
    private String tenantId;

    @Schema(description = "归属活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    @Pattern(regexp = SmartCodeUtil.BIZ_CODE_REGEX, message = "活动" + SmartCodeUtil.BIZ_CODE_MESSAGE)
    private String activityCode;

    @Schema(description = "优惠配置ID，关联 t_promotion_config，承载预算与风控", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "优惠配置 不能为空")
    private Long promotionConfigId;

    @Schema(description = "资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "资产类型 不能为空")
    private String prizeType;

    @Schema(description = "奖品名称")
    private String prizeName;

    @Schema(description = "奖品编码：10 位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖品编码 不能为空")
    @Pattern(regexp = SmartCodeUtil.BIZ_CODE_REGEX, message = "奖品" + SmartCodeUtil.BIZ_CODE_MESSAGE)
    private String prizeCode;

    @Schema(description = "奖品级别")
    private Integer prizeLevel;

    @Schema(description = "奖励价值")
    private BigDecimal prizeValue;

    @Schema(description = "审批模式：0-自动免审, 1-人工审批", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审批模式：0-自动免审, 1-人工审批 不能为空")
    private Integer approveMode;

    @Schema(description = "排序权重")
    private Integer sortWeight;

    @Schema(description = "扩展信息：如奖品图片URL、跳转链接等")
    private String ext;

}