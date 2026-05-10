package net.lab1024.sa.risk.promotionconfig.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 优惠配置表 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */

@Data
public class PromotionConfigVO {


    @Schema(description = "配置ID")
    private Long id;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "优惠配置名称")
    private String promoName;

    @Schema(description = "资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物)")
    private String prizeType;

    @Schema(description = "总库存(个数)：-1为不限制(适用于券/实物)")
    private Integer totalQuota;

    @Schema(description = "已消耗库存(个数)")
    private Integer usedQuota;

    @Schema(description = "总预算(金额)：-1为不限制(适用于积分/现金)")
    private BigDecimal totalAmount;

    @Schema(description = "已消耗预算(金额)")
    private BigDecimal usedAmount;

    @Schema(description = "审核层级控制：0-无需审核, 1-单层审批, 2-双层审批")
    private Integer reviewLevel;

    @Schema(description = "一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审)")
    private BigDecimal firstReviewThreshold;

    @Schema(description = "二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2)")
    private BigDecimal secondReviewThreshold;

    @Schema(description = "单次最大数量兜底，超限阻断")
    private Integer singleMaxQuota;

    @Schema(description = "单次最大金额兜底，超限阻断")
    private BigDecimal singleMaxAmount;

    @Schema(description = "限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM")
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

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
