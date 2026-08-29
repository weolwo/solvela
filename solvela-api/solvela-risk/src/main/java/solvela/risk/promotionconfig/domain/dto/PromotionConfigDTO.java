package solvela.risk.promotionconfig.domain.dto;


import solvela.enums.ReviewLevelEnum;
import solvela.enums.EnableStatusEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 营销配置列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * 完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class PromotionConfigDTO {


    /** 配置ID */
    private Long id;

    /** 优惠配置名称 */
    private String promoName;

    /** 资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物) */
    private String prizeType;

    /** 总库存(个数)：-1为不限制(适用于券/实物) */
    private Integer totalQuota;

    /** 已消耗库存(个数) */
    private Integer usedQuota;

    /** 总预算(金额)：-1为不限制(适用于积分/现金) */
    private BigDecimal totalAmount;

    /** 已消耗预算(金额) */
    private BigDecimal usedAmount;

    /** 审核层级控制：0-无需审核, 1-单层审批, 2-双层审批 */
    private ReviewLevelEnum reviewLevel;

    /** 一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审) */
    private BigDecimal firstReviewThreshold;

    /** 二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2) */
    private BigDecimal secondReviewThreshold;

    /** 单次最大数量兜底，超限阻断 */
    private Integer singleMaxQuota;

    /** 单次最大金额兜底，超限阻断 */
    private BigDecimal singleMaxAmount;

    /** 限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM */
    private String limitPeriod;

    /** 同周期内，单会员ID最多领取次数 (-1为不限) */
    private Integer identifyLimit;

    /** 同周期内，单手机号最多领取次数 (-1为不限) */
    private Integer phoneLimit;

    /** 同周期内，单IP地址最多领取次数 (-1为不限) */
    private Integer ipLimit;

    /** 同周期内，单设备硬件号(IMEI/IDFA)最多领取次数 (-1为不限) */
    private Integer deviceLimit;

    /** 同周期内，单客户端指纹最多领取次数 (-1为不限) */
    private Integer fingerprintLimit;

    /** 互斥规则：存互斥的优惠配置ID数组 */
    private String mutexRule;

    /** 状态：0-停用, 1-启用 */
    private EnableStatusEnum status;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
