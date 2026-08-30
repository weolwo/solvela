package solvela.risk;

import solvela.enums.ReviewLevelEnum;
import solvela.enums.EnableStatusEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 优惠配置表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */

@Data
@TableName("t_promotion_config")
public class PromotionConfig {

    /**
     * 配置ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属优惠配置分组ID，关联 {@code t_promotion_group}。
     *
     * <p><b>可空</b>：分组是 2026-08-30 加的配置入口，此前建的独立配置继续按原样工作，
     * 不做存量迁移。为空即「不属于任何分组」。
     *
     * <p>唯一索引 {@code uk_group_prize_type (group_id, prize_type)} 保证组内一种资产类型
     * 只有一条配置 —— 这是工作台「按奖励类型定位到具体配置」能成立的前提。
     * MySQL 的唯一索引不约束 NULL，所以存量的未分组配置不受影响。
     *
     * <p>⚠️ 发奖链路<b>不认识</b>这一列：{@code t_prize_config} 关联的仍是具体的
     * {@code promotion_config_id}，分组只在管理端用。
     */
    private Long groupId;

    /**
     * 优惠配置名称
     */
    private String promoName;

    /**
     * 资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物), MARKER(标记)
     */
    private String prizeType;

    /**
     * 总库存(个数)：-1为不限制(适用于券/实物)
     */
    private Integer totalQuota;

    /**
     * 已消耗库存(个数)
     */
    private Integer usedQuota;

    /**
     * 总预算(金额)：-1为不限制(适用于积分/现金)
     */
    private BigDecimal totalAmount;

    /**
     * 已消耗预算(金额)
     */
    private BigDecimal usedAmount;

    /**
     * 审核层级控制：0-无需审核, 1-单层审批, 2-双层审批
     */
    private ReviewLevelEnum reviewLevel;

    /**
     * 一审触发阈值：动账金额 >= 此值必须一审(值为0代表笔笔一审)
     */
    private BigDecimal firstReviewThreshold;

    /**
     * 二审触发阈值：动账金额 >= 此值必须二审(前提 review_level=2)
     */
    private BigDecimal secondReviewThreshold;

    /**
     * 单次最大数量兜底，超限阻断
     */
    private Integer singleMaxQuota;

    /**
     * 单次最大金额兜底，超限阻断
     */
    private BigDecimal singleMaxAmount;

    /**
     * 限制周期：LIFETIME(终身), DAILY(每日), WEEKLY(每周), MONTHLY(每月), CUSTOM
     */
    private String limitPeriod;

    /**
     * 限制周期为 CUSTOM 时的窗口开始时间；其余周期留空。
     *
     * <p>刻意<b>不与活动周期联动</b>：优惠配置是可跨活动复用的预算池，
     * 表上没有、也不该有活动关联列。要「和活动周期一样」就把活动的起止时间抄进来。
     */
    private LocalDateTime limitStartTime;

    /**
     * 限制周期为 CUSTOM 时的窗口结束时间；其余周期留空。
     *
     * <p>它决定 {@code FrequencyRiskFilter} 里那个计数键的 TTL —— 窗口一过计数自然清零。
     */
    private LocalDateTime limitEndTime;

    /**
     * 同周期内，单会员ID最多领取次数 (-1为不限)
     */
    private Integer identifyLimit;

    /**
     * 同周期内，单手机号最多领取次数 (-1为不限)
     */
    private Integer phoneLimit;

    /**
     * 同周期内，单IP地址最多领取次数 (-1为不限)
     */
    private Integer ipLimit;

    /**
     * 同周期内，单设备硬件号(IMEI/IDFA)最多领取次数 (-1为不限)
     */
    private Integer deviceLimit;

    /**
     * 同周期内，单客户端指纹最多领取次数 (-1为不限)
     */
    private Integer fingerprintLimit;

    /**
     * 互斥规则：存互斥的优惠配置ID数组
     */
    private String mutexRule;

    /**
     * 状态：0-停用, 1-启用
     */
    private EnableStatusEnum status;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
