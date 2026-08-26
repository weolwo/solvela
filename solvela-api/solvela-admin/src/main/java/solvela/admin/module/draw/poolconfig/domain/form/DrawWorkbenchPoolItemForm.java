package solvela.admin.module.draw.poolconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 抽奖工作台 Tab1 奖项物资项（t_prize_pool_item）
 * SKU 化：名称/价值等展示信息以 prizeCode 关联 t_prize_config，本表单只收抽奖专有属性
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawWorkbenchPoolItemForm {

    @Schema(description = "奖品编码，关联 t_prize_config", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖品编码 不能为空")
    private String prizeCode;

    @Schema(description = "总库存：-1 不限量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "总库存 不能为空")
    private Integer totalStock;

    @Schema(description = "单人限领次数：-1 不限", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "单人限领次数 不能为空")
    private Integer userMaxCount;

    @Schema(description = "白名单：名单内用户库存充足时必中")
    private List<String> whiteList;
}
