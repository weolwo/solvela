package sa.draw.poolconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 抽奖工作台 聚合保存表单（主子表：t_prize_pool_item + t_prize_pool_config + t_pool_prize_mapping）
 * 结构与前端契约一致：{ activityCode, prizeItemList, poolList }
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawWorkbenchSaveForm {

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    @Schema(description = "Tab1 奖项物资列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "奖项物资 至少配置一项")
    @Valid
    private List<DrawWorkbenchPoolItemForm> prizeItemList;

    @Schema(description = "Tab2 奖池列表（多池）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少配置一个奖池")
    @Valid
    private List<DrawWorkbenchPoolForm> poolList;
}
