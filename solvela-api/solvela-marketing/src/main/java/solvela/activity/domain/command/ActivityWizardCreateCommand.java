package solvela.activity.domain.command;

import solvela.enums.ApproveModeEnum;
import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.validation.enumeration.CheckEnum;
import solvela.enums.ActivityTypeEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动创建向导的<b>领域命令</b>。与管理端的 {@code ActivityWizardCreateCommand} 形状一致，但职责不同：
 *
 * <ul>
 *   <li>Form 是 HTTP 请求体：{@code @Schema} 描述接口文档、{@code @NotNull} 等校验
 *       前端传没传、传得对不对 —— 这些都跟着某个端的页面走；</li>
 *   <li>Command 是领域入参：service 对它做的是<b>业务不变量</b>校验
 *       （编码是否重复、状态能否流转、关联配置是否匹配），与谁调用无关。</li>
 * </ul>
 *
 * <p>合成一个的代价：C 端将来若要写入，得构造一个带管理端校验规则的表单；
 * 而共享层也会一直依赖 springdoc 与 jakarta.validation 这些 HTTP 层的概念。
 *
 * <p>分层说明见 {@code MemberWalletQuery}。
 */
@Data
public class ActivityWizardCreateCommand {

    /** 活动编码：10 位大写字母+数字，全局唯一 */
    private String activityCode;

    /** 活动名称 */
    private String activityName;

    /** 活动类型：BASIC/DRAW/TASK/LOTTERY */
    @CheckEnum(value = ActivityTypeEnum.class, required = true, message = "活动类型非法")
    private String activityType;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 数据截止时间：此刻起不再受理参与，但奖品仍可领到 endTime。为空表示与 endTime 相同 */
    private LocalDateTime dataEndTime;

    /**
     * 随活动一起创建的奖品，可为空 —— 建活动时不强制配奖品。
     * <p>
     * 强制的话，「我就想先把活动壳子建了」这个很常见的诉求会变难，
     * 而向导第二步的空态本来就会提示去建奖品。
     */
    private List<WizardPrizeCommand> prizeList;

    /**
     * 向导里快速创建奖品的精简入参。
     * <p>
     * 刻意不复用 PrizeConfigAddCommand：那个表单里 activityCode 是必填的，
     * 而在这里它由外层活动决定，让调用方再填一遍既冗余又给了填错的机会。
     */
    @Data
    public static class WizardPrizeCommand {

        /** 奖品编码：10 位大写字母+数字，全局唯一 */
        private String prizeCode;

        /** 奖品名称 */
        private String prizeName;

        /** 资产类型：SCORE/BALANCE/COUPON/PHYSICAL 等 */
        private String prizeType;

        /** 优惠配置ID，承载预算与风控；服务端会校验其资产类型与奖品一致 */
        private Long promotionConfigId;

        /** 奖励价值 */
        private java.math.BigDecimal prizeValue;

        /** 审批模式：0-自动免审, 1-人工审批 */
        private ApproveModeEnum approveMode;

        /** 奖品级别，可空 */
        private Integer prizeLevel;

        /** 排序权重，可空 */
        private Integer sortWeight;
    }
}
