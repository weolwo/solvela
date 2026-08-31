package solvela.admin.module.draw.drawconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;
import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;
import solvela.base.validation.enumeration.CheckEnum;

/**
 * 抽奖配置新增/修改。
 *
 * <p>🔴 修改时 {@code drawCode} 与 {@code activityCode} 会被服务端忽略：
 * 前者是脚本挂载的引用键，改了等于把已有挂载指向一个不存在的对象；
 * 后者改了就是把整套抽奖搬到别的活动下，那不是「编辑」该干的事。
 */
@Data
public class DrawConfigForm {

    @Schema(description = "主键id，修改时必填")
    private Long id;

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码不能为空")
    private String activityCode;

    @Schema(description = "抽奖配置编码。脚本挂载用的就是它，新增时可点「生成」", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "抽奖配置编码不能为空")
    @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = SolvelaCodeUtil.BIZ_CODE_MESSAGE)
    private String drawCode;

    @Schema(description = "抽奖名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "抽奖名称不能为空")
    @Size(max = 128, message = "抽奖名称最长 128 个字符")
    private String drawName;

    @Schema(description = "抽奖算法：1-按概率, 2-按库存比例（第二种尚未实现）")
    @CheckEnum(value = DrawModeEnum.class, message = "抽奖算法不合法")
    private DrawModeEnum drawMode;

    @Schema(description = "重置周期：DAY/WEEK/MONTH/ACTIVITY。重置的是单人限领计数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "重置周期不能为空")
    @Pattern(regexp = "^(DAY|WEEK|MONTH|ACTIVITY)$", message = "重置周期只能是 DAY/WEEK/MONTH/ACTIVITY")
    private String resetPeriod;

    @Schema(description = "状态：0-关闭, 1-开启")
    @CheckEnum(value = EnableStatusEnum.class, message = "状态不合法")
    private EnableStatusEnum status;
}
