package net.lab1024.sa.risk.promotionconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 优惠配置表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */

@Data
public class PromotionConfigAddForm {

    @Schema(description = "租户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户ID 不能为空")
    private String tenantId;

    @Schema(description = "优惠配置名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "优惠配置名称 不能为空")
    private String promoName;

    @Schema(description = "资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物) 不能为空")
    private String prizeType;

    @Schema(description = "总库存(个数)：-1为不限制(适用于券/实物)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "总库存(个数)：-1为不限制(适用于券/实物) 不能为空")
    private Integer totalQuota;

    @Schema(description = "已消耗库存(个数)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "已消耗库存(个数) 不能为空")
    private Integer usedQuota;

    @Schema(description = "总预算(金额)：-1为不限制(适用于积分/现金)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "总预算(金额)：-1为不限制(适用于积分/现金) 不能为空")
    private BigDecimal totalAmount;

    @Schema(description = "已消耗预算(金额)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "已消耗预算(金额) 不能为空")
    private BigDecimal usedAmount;

    @Schema(description = "审核层级控制：0-无需审核, 1-单层审批, 2-双层审批", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审核层级控制：0-无需审核, 1-单层审批, 2-双层审批 不能为空")
    private Integer reviewLevel;

    @Schema(description = "一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审) 不能为空")
    private BigDecimal firstReviewThreshold;

    @Schema(description = "二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2) 不能为空")
    private BigDecimal secondReviewThreshold;

    @Schema(description = "单次最大数量兜底，超限阻断", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "单次最大数量兜底，超限阻断 不能为空")
    private Integer singleMaxQuota;

    @Schema(description = "单次最大金额兜底，超限阻断", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "单次最大金额兜底，超限阻断 不能为空")
    private BigDecimal singleMaxAmount;

    @Schema(description = "限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM 不能为空")
    private String limitPeriod;

    @Schema(description = "同周期内，单会员ID最多领取次数 (-1为不限)")
    private Integer identifyLimit;

    @Schema(description = "同周期内，单手机号最多领取次数 (-1为不限)")
    private Integer phoneLimit;

    @Schema(description = "同周期内，单IP地址最多领取次数 (-1为不限)")
    private Integer ipLimit;

    @Schema(description = "同周期内，单设备硬件号(IMEI/IDFA)最多领取次数 (-1为不限)")
    private Integer deviceLimit;

    @Schema(description = "同周期内，单客户端指纹最多领取次数 (-1为不限)")
    private Integer fingerprintLimit;

    @Schema(description = "互斥规则：存互斥的优惠配置ID数组")
    private String mutexRule;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}