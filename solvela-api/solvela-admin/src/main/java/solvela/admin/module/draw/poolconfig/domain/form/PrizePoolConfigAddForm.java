package solvela.admin.module.draw.poolconfig.domain.form;

import solvela.enums.PrizePoolStatusEnum;
import solvela.enums.DrawModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

/**
 * 奖池配置 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */

@Data
public class PrizePoolConfigAddForm {

    @Schema(description = "归属活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = "活动" + SolvelaCodeUtil.BIZ_CODE_MESSAGE)
    private String activityCode;

    @Schema(description = "奖池编码：10 位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池编码 不能为空")
    @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = "奖池" + SolvelaCodeUtil.BIZ_CODE_MESSAGE)
    private String poolCode;

    @Schema(description = "奖池名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池名称 不能为空")
    private String poolName;

    @Schema(description = "0关闭，1开启")
    private PrizePoolStatusEnum status;

}