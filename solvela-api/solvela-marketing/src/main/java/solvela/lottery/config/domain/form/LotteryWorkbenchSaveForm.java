package solvela.lottery.config.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import solvela.base.common.util.SolvelaCodeUtil;

import java.util.List;

/**
 * 彩票工作台 聚合保存表单（主子表：t_lottery_config + t_lottery_prize_rule）
 *
 * 契约：{ lotteryCode, lotteryName, numberLength, totalCount, activityCode, prizeRuleList }
 *
 * ⚠️ 刻意不含 numberCharset：号码固定十进制，不是可配项（见 LotteryConst.NUMBER_CHARSET）。
 * 前端既不提交也不展示，服务端保存时固定写死，避免「界面摆着可选项、选了却报错」。
 *
 * @Author alaric
 * @Date 2026-07-27
 */
@Data
public class LotteryWorkbenchSaveForm {

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    /**
     * 彩票编码允许手工输入，故这里挂 @Pattern 做格式校验，Service 里还要再判一次重
     * （表上的唯一索引直接抛 SQL 异常对运营不友好）——这是铁律 8 的「落点清单」第 ① 项
     */
    @Schema(description = "彩票编码：10位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = "彩票" + SolvelaCodeUtil.BIZ_CODE_MESSAGE)
    private String lotteryCode;

    @Schema(description = "彩票名称（外显）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票名称 不能为空")
    private String lotteryName;

    /**
     * 上下限与 LotteryConst.MIN_NUMBER_LENGTH / MAX_NUMBER_LENGTH 同源。
     * 注解参数必须是编译期常量，故这里只能写字面量，改动时两处要一起改。
     */
    @Schema(description = "号码长度，取值 4~9（纯数字）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "号码长度 不能为空")
    @Min(value = 4, message = "号码长度不能小于 4 位：号码空间需不小于 1 万")
    @Max(value = 9, message = "号码长度不能大于 9 位")
    private Integer numberLength;

    @Schema(description = "单期发售上限", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "单期发售上限 不能为空")
    @Min(value = 1, message = "单期发售上限必须大于 0")
    private Integer totalCount;

    /**
     * 允许为空：运营可以先把发号引擎配好，奖级规则稍后再配。
     * 但一旦提交了规则，就要整体重建（子表整表替换语义）
     */
    @Schema(description = "奖级规则列表")
    @Valid
    private List<LotteryWorkbenchRuleForm> prizeRuleList;
}
