package solvela.admin.module.draw.poolconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import solvela.base.validation.enumeration.CheckEnum;
import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;

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

    @Schema(description = "抽奖配置编码。已有配置时服务端忽略——它是脚本挂载的引用键")
    private String drawCode;

    @Schema(description = "抽奖名称")
    @Size(max = 128, message = "抽奖名称最长 128 个字符")
    private String drawName;

    @Schema(description = "重置周期：DAY/WEEK/MONTH/ACTIVITY。同时决定限领计数桶与「已抽几次」的时间下界")
    @Pattern(regexp = "^(DAY|WEEK|MONTH|ACTIVITY)$", message = "重置周期只能是 DAY/WEEK/MONTH/ACTIVITY")
    private String resetPeriod;

    @Schema(description = "抽奖算法：1-按概率, 2-按库存比例（第二种尚未实现）")
    @CheckEnum(value = DrawModeEnum.class, message = "抽奖算法不合法")
    private DrawModeEnum drawMode;

    @Schema(description = "抽奖开关：0-关闭, 1-开启")
    @CheckEnum(value = EnableStatusEnum.class, message = "抽奖开关不合法")
    private EnableStatusEnum drawStatus;

    @Schema(description = "Tab1 奖项物资列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "奖项物资 至少配置一项")
    @Valid
    private List<DrawWorkbenchPoolItemForm> prizeItemList;

    @Schema(description = "Tab2 奖池列表（多池）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少配置一个奖池")
    @Valid
    private List<DrawWorkbenchPoolForm> poolList;
}
