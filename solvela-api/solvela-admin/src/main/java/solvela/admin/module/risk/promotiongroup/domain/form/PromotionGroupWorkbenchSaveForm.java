package solvela.admin.module.risk.promotiongroup.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import solvela.enums.EnableStatusEnum;
import solvela.enums.ReviewLevelEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠配置分组工作台 聚合保存表单（HTTP 侧）。
 *
 * <p>与领域命令 {@code PromotionGroupWorkbenchSaveCommand} 形状一致但职责不同：
 * 这里管「前端传没传、传得对不对」，业务不变量（类型合不合法、组内会不会重复、
 * 停用组的子项要不要一起关）在 Service 里判。分层理由见 {@code ActivityWizardCreateCommand}。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
public class PromotionGroupWorkbenchSaveForm {

    @Schema(description = "分组ID：为空表示新建")
    private Long id;

    @Schema(description = "分组名称，如「2026中秋活动优惠配置」", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分组名称 不能为空")
    private String groupName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "分组状态：0-停用, 1-启用。停用会连带把组内所有配置一起停用")
    private EnableStatusEnum status;

    @Schema(description = "组内各资产类型的配置。没出现在这里、但库里已存在的类型会被停用（不是删除）")
    private List<PromotionGroupItemForm> itemList;

    @Data
    public static class PromotionGroupItemForm {

        @Schema(description = "已存在的配置ID。服务端不采信它定位记录，只用来判断是不是新增")
        private Long id;

        @Schema(description = "资产类型：SCORE / BALANCE / COUPON / PHYSICAL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "资产类型 不能为空")
        private String prizeType;

        @Schema(description = "配置名称：留空由服务端按「分组名-类型」生成")
        private String promoName;

        @Schema(description = "总库存(个数)：-1为不限制(适用于券/实物)")
        private Integer totalQuota;

        @Schema(description = "总预算(金额)：-1为不限制(适用于积分/现金)")
        private BigDecimal totalAmount;

        @Schema(description = "审核层级控制：0-无需审核, 1-单层审批, 2-双层审批")
        private ReviewLevelEnum reviewLevel;

        @Schema(description = "一审触发阈值；不适用的层级由服务端归零")
        private BigDecimal firstReviewThreshold;

        @Schema(description = "二审触发阈值；暂未生效")
        private BigDecimal secondReviewThreshold;

        @Schema(description = "单次最大数量兜底，超限阻断")
        private Integer singleMaxQuota;

        @Schema(description = "单次最大金额兜底，超限阻断")
        private BigDecimal singleMaxAmount;

        @Schema(description = "限制周期：LIFETIME / DAILY / WEEKLY / MONTHLY / CUSTOM")
        private String limitPeriod;

        @Schema(description = "限制周期为 CUSTOM 时的窗口开始时间")
        private LocalDateTime limitStartTime;

        @Schema(description = "限制周期为 CUSTOM 时的窗口结束时间")
        private LocalDateTime limitEndTime;

        @Schema(description = "同周期内，单会员ID最多领取次数 (-1为不限)")
        private Integer identifyLimit;

        @Schema(description = "同周期内，单手机号最多领取次数 (-1为不限)；暂未生效")
        private Integer phoneLimit;

        @Schema(description = "同周期内，单IP地址最多领取次数 (-1为不限)；暂未生效")
        private Integer ipLimit;

        @Schema(description = "同周期内，单设备硬件号最多领取次数 (-1为不限)；暂未生效")
        private Integer deviceLimit;

        @Schema(description = "同周期内，单客户端指纹最多领取次数 (-1为不限)；暂未生效")
        private Integer fingerprintLimit;

        @Schema(description = "互斥规则：互斥的优惠配置ID数组（json 文本）；暂未生效")
        private String mutexRule;

        @Schema(description = "这个类型启不启用。分组停用时服务端会强制落停用")
        private EnableStatusEnum status;
    }
}
