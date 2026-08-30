package solvela.risk.promotionconfig.domain.command;


import solvela.enums.ReviewLevelEnum;
import java.time.LocalDateTime;
import solvela.enums.EnableStatusEnum;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 更新营销配置的<b>领域命令</b>。与管理端的 {@code PromotionConfigUpdateCommand} 形状一致，但职责不同：
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
public class PromotionConfigUpdateCommand {

    /** 配置ID */
    private Long id;

    /** 优惠配置名称 */
    private String promoName;

    /** 资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物) */
    private String prizeType;

    /** 总库存(个数)：-1为不限制(适用于券/实物) */
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

    /** 总预算(金额)：-1为不限制(适用于积分/现金) */
    private BigDecimal totalAmount;

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

    /** 限制周期为 CUSTOM 时的窗口开始时间；其余周期留空。校验见 PromotionConfigService.validateLimitWindow */
    private LocalDateTime limitStartTime;

    /** 限制周期为 CUSTOM 时的窗口结束时间；其余周期留空 */
    private LocalDateTime limitEndTime;

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

}