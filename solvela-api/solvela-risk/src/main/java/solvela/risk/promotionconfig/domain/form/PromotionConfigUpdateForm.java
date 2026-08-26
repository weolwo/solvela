package solvela.risk.promotionconfig.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 优惠配置表 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */

@Data
public class PromotionConfigUpdateForm {

    @Schema(description = "配置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "配置ID 不能为空")
    private Long id;

    @Schema(description = "优惠配置名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "优惠配置名称 不能为空")
    private String promoName;

    @Schema(description = "资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物) 不能为空")
    private String prizeType;

    @Schema(description = "总库存(个数)：-1为不限制(适用于券/实物)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "总库存(个数)：-1为不限制(适用于券/实物) 不能为空")
    private Integer totalQuota;

    // 🔴 used_quota / used_amount 刻意不在表单里，这是一处资损修复：
    //
    // 这两列由 PromotionConfigMapper 里的 CAS 式原子 SQL 维护：
    //   set used_amount = used_amount + #{amount}
    //   where (total_amount = -1 or total_amount - used_amount >= #{amount})
    // 而本表单走的是 updateById 全字段覆写，写进去的是**运营打开弹窗那一刻的快照值**。
    // 于是「进后台改个名字点保存」会把这期间所有并发扣减一笔勾销 ——
    // used_amount 回退，GlobalBudgetRiskFilter 拿 totalAmount - usedAmount 判预算，
    // 闸门就被重新打开了，且现场没有任何报错。
    //
    // 去掉字段后实体里这两个属性为 null，MyBatis-Plus 默认 FieldStrategy.NOT_NULL
    // 会把它们排除在 UPDATE 语句外（全工程没有改过该策略），原值得以保留。

    @Schema(description = "总预算(金额)：-1为不限制(适用于积分/现金)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "总预算(金额)：-1为不限制(适用于积分/现金) 不能为空")
    private BigDecimal totalAmount;

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

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer status;

}