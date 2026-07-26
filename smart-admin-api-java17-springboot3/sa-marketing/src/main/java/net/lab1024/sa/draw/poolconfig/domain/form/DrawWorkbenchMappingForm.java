package net.lab1024.sa.draw.poolconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 抽奖工作台 Tab2 奖池坑位映射项（t_pool_prize_mapping）
 * 前端以 prizeCode 表达关联，服务端保存时解析为 prize_item_id
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawWorkbenchMappingForm {

    @Schema(description = "奖品编码，须存在于本次提交的物资列表中", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖品编码 不能为空")
    private String prizeCode;

    @Schema(description = "中奖概率（百分比，0~100，支持4位小数）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "中奖概率 不能为空")
    @DecimalMin(value = "0", message = "中奖概率不能为负数")
    @DecimalMax(value = "100", message = "中奖概率不能超过100")
    private BigDecimal probability;

    @Schema(description = "是否兜底奖项（编辑期概念，概率已配平为具体数值，暂不落库）")
    private Boolean isFallback;
}
