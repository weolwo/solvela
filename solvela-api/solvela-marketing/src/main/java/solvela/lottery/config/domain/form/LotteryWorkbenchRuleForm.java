package solvela.lottery.config.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 彩票工作台 奖级规则节点（t_lottery_prize_rule 的一行）
 *
 * @Author alaric
 * @Date 2026-07-27
 */
@Data
public class LotteryWorkbenchRuleForm {

    @Schema(description = "奖级，1 为最高", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "奖级 不能为空")
    @Min(value = 1, message = "奖级必须从 1 开始")
    private Integer prizeLevel;

    @Schema(description = "匹配规则：EXACT-全号 / TAIL-尾号 / HEAD-首号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "匹配规则 不能为空")
    private String matchRule;

    @Schema(description = "匹配长度，EXACT 时等于号码长度", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "匹配长度 不能为空")
    @Min(value = 1, message = "匹配长度必须大于 0")
    private Integer matchLength;

    @Schema(description = "绑定的奖品编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖级必须绑定奖品")
    private String prizeCode;
}
