package solvela.admin.module.activity.domain.form;

import solvela.enums.ApproveModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.validation.enumeration.CheckEnum;
import solvela.enums.ActivityTypeEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动创建向导第一步的聚合入参：活动 + 若干奖品，一次事务落库。
 *
 * <h3>为什么要聚合，而不是前端先建活动再循环调 /prizeConfig/add</h3>
 * 奖品的 activityCode 是 @NotBlank，也就是<b>活动必须先存在</b>，奖品才建得出来。
 * 若由前端串行发起，中途任一奖品失败就会留下「活动建好了、奖品只建了一半」的残局 ——
 * 而运营在界面上看到的是一个失败提示，他不会知道库里已经躺了一个半成品活动。
 * <p>
 * 放进同一个事务后，要么全成，要么全不成，界面上的成败与库里的状态永远一致。
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
@Data
public class ActivityWizardCreateForm {

    @Schema(description = "活动编码：10 位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = "活动" + SolvelaCodeUtil.BIZ_CODE_MESSAGE)
    private String activityCode;

    @Schema(description = "活动名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动名称 不能为空")
    private String activityName;

    @Schema(description = "活动类型：BASIC/DRAW/TASK/LOTTERY", requiredMode = Schema.RequiredMode.REQUIRED)
    @CheckEnum(value = ActivityTypeEnum.class, required = true, message = "活动类型非法")
    private String activityType;

    @Schema(description = "活动开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "活动开始时间 不能为空")
    private LocalDateTime startTime;

    @Schema(description = "活动结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "活动结束时间 不能为空")
    private LocalDateTime endTime;

    @Schema(description = "数据截止时间：此刻起不再受理参与（抽奖/任务累计），但活动仍可见、已中的奖仍可领到活动结束时间。不填表示与活动结束时间相同")
    private LocalDateTime dataEndTime;

    /**
     * 随活动一起创建的奖品，可为空 —— 建活动时不强制配奖品。
     * <p>
     * 强制的话，「我就想先把活动壳子建了」这个很常见的诉求会变难，
     * 而向导第二步的空态本来就会提示去建奖品。
     */
    @Schema(description = "随活动一起创建的奖品列表，可为空")
    @Valid
    private List<WizardPrizeForm> prizeList;

    /**
     * 向导里快速创建奖品的精简入参。
     * <p>
     * 刻意不复用 PrizeConfigAddForm：那个表单里 activityCode 是必填的，
     * 而在这里它由外层活动决定，让调用方再填一遍既冗余又给了填错的机会。
     */
    @Data
    public static class WizardPrizeForm {

        @Schema(description = "奖品编码：10 位大写字母+数字，全局唯一", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "奖品编码 不能为空")
        @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = "奖品" + SolvelaCodeUtil.BIZ_CODE_MESSAGE)
        private String prizeCode;

        @Schema(description = "奖品名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "奖品名称 不能为空")
        private String prizeName;

        @Schema(description = "资产类型：SCORE/BALANCE/COUPON/PHYSICAL 等", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "资产类型 不能为空")
        private String prizeType;

        /**
         * 优惠配置ID，承载预算与风控；服务端会校验其资产类型与奖品一致。
         *
         * <p>与 {@code PrizeConfigAddForm} 同理，必填与否取决于 prizeType，
         * 所以这里不挂 {@code @NotNull}，交给 checkPromotionConfigMatch 按类型判。
         */
        @Schema(description = "优惠配置ID，承载预算与风控；标记(MARKER)类奖品不需要，留空即可")
        private Long promotionConfigId;

        @Schema(description = "奖励价值", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "奖励价值 不能为空")
        private java.math.BigDecimal prizeValue;

        @Schema(description = "审批模式：0-自动免审, 1-人工审批", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "审批模式 不能为空")
        private ApproveModeEnum approveMode;

        @Schema(description = "奖品级别，可空")
        private Integer prizeLevel;

        @Schema(description = "排序权重，可空")
        private Integer sortWeight;
    }
}
