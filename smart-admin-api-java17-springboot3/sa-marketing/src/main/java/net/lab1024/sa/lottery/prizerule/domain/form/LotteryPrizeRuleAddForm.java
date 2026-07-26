package net.lab1024.sa.lottery.prizerule.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票奖励配置 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */

@Data
public class LotteryPrizeRuleAddForm {

    @Schema(description = "租户id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户id 不能为空")
    private String tenantId;

    @Schema(description = "彩票编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    private String lotteryCode;

    @Schema(description = "奖品奖级", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "奖品奖级 不能为空")
    private Integer prizeLevel;

    @Schema(description = "匹配规则,EXACT:全号, TAIL:尾号匹配, HEAD:首号匹配", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "匹配规则,EXACT:全号, TAIL:尾号匹配, HEAD:首号匹配 不能为空")
    private String matchRule;

    @Schema(description = "匹配长度", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "匹配长度 不能为空")
    private Integer matchLength;

    @Schema(description = "奖品编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖品编码 不能为空")
    private String prizeCode;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}