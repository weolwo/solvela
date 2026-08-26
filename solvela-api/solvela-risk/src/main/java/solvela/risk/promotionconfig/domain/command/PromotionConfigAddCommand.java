package solvela.risk.promotionconfig.domain.command;


import java.math.BigDecimal;

import lombok.Data;

/**
 * 新增营销配置的<b>领域命令</b>。与管理端的 {@code PromotionConfigAddCommand} 形状一致，但职责不同：
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
public class PromotionConfigAddCommand {

    /** 优惠配置名称 */
    private String promoName;

    /** 资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物) */
    private String prizeType;

    /** 总库存(个数)：-1为不限制(适用于券/实物) */
    private Integer totalQuota;

    // ⚠️ used_quota / used_amount 刻意不在表单里：它们是预算扣减的运行态计数器，
    // 只能由 PromotionConfigMapper 里那条 CAS 式原子 SQL 维护
    // （used_amount = used_amount + #{amount} WHERE total_amount - used_amount >= #{amount}）。
    // 让管理端传值意味着新建时能凭空指定「已消耗」，等于开局就把预算闸门拨到任意位置。

    /** 总预算(金额)：-1为不限制(适用于积分/现金) */
    private BigDecimal totalAmount;

    /** 审核层级控制：0-无需审核, 1-单层审批, 2-双层审批 */
    private Integer reviewLevel;

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

}