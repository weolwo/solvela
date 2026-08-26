package solvela.admin.module.draw.poolconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 抽奖工作台 Tab2 奖池节点（t_prize_pool_config + 其坑位映射）
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawWorkbenchPoolForm {

    @Schema(description = "奖池编码，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池编码 不能为空")
    private String poolCode;

    @Schema(description = "奖池名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池名称 不能为空")
    private String poolName;

    @Schema(description = "坑位映射列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "奖池至少配置一个奖项")
    @Valid
    private List<DrawWorkbenchMappingForm> prizeMappingList;
}
