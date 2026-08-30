package solvela.risk.promotiongroup.domain.command;

import lombok.Data;
import solvela.enums.EnableStatusEnum;
import solvela.enums.ReviewLevelEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠配置分组工作台 聚合保存命令（主子表：t_promotion_group + t_promotion_config）。
 *
 * <h3>🔴 子表不是「整表替换」语义</h3>
 * 彩票工作台的 {@code workbenchSave} 是先 {@code remove()} 再逐条 {@code insert()}，
 * <b>那套写法在这里会造成资损</b>，三个理由：
 * <ul>
 *   <li>{@code used_quota} / {@code used_amount} 是运行态计数器，删了重建等于把已消耗预算清零，
 *       预算闸门直接重置到满水位；</li>
 *   <li>{@code t_prize_config.promotion_config_id} 指着它，重建后全变悬挂 ID；</li>
 *   <li>{@code t_proposal_record.promotion_config_id} 同理，历史提案对账断链。</li>
 * </ul>
 * 所以这里是 <b>按 (group_id, prize_type) upsert，移除走停用而不是删除</b>。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
public class PromotionGroupWorkbenchSaveCommand {

    /** 分组ID：为空表示新建 */
    private Long id;

    /** 分组编码：新建时由服务端生成，编辑时不可改 */
    private String groupCode;

    /** 分组名称，如「2026中秋活动优惠配置」 */
    private String groupName;

    /** 备注 */
    private String remark;

    /** 分组状态：0-停用, 1-启用 */
    private EnableStatusEnum status;

    /**
     * 组内各资产类型的配置。允许为空 —— 可以先把组建出来、稍后再配类型。
     *
     * <p>没出现在这个列表里、但库里已存在的类型会被<b>停用</b>（不是删除）。
     */
    private List<PromotionGroupItemCommand> itemList;

    /**
     * 组内一种资产类型的配置。字段与 {@code t_promotion_config} 一一对应，
     * 但<b>刻意不含 usedQuota / usedAmount</b>：那是运行态计数器，只能由发放链路的原子 SQL 维护，
     * 让管理端传值等于开局就能把预算闸门拨到任意位置。
     */
    @Data
    public static class PromotionGroupItemCommand {

        /**
         * 已存在的配置ID。为空表示这个类型是本次新增的。
         *
         * <p>服务端<b>不采信</b>它来定位记录 —— 定位一律按 (groupId, prizeType) 重查，
         * 否则前端传错一个 ID 就会把别的组的配置改掉。它只用来判断是不是新增。
         */
        private Long id;

        /** 资产类型：SCORE / BALANCE / COUPON / PHYSICAL。MARKER 不进组（它不需要优惠配置） */
        private String prizeType;

        /** 配置名称：留空时由服务端按「分组名-类型」生成，运营不必给每条子配置起名 */
        private String promoName;

        /** 总库存(个数)：-1为不限制(适用于券/实物) */
        private Integer totalQuota;

        /** 总预算(金额)：-1为不限制(适用于积分/现金) */
        private BigDecimal totalAmount;

        /** 审核层级控制：0-无需审核, 1-单层审批, 2-双层审批 */
        private ReviewLevelEnum reviewLevel;

        /** 一审触发阈值。不适用的层级由服务端归零，见 PromotionConfigService.normalizeForSave */
        private BigDecimal firstReviewThreshold;

        /** 二审触发阈值。目前全链路无消费方，页面已标注「暂未生效」 */
        private BigDecimal secondReviewThreshold;

        /** 单次最大数量兜底，超限阻断 */
        private Integer singleMaxQuota;

        /** 单次最大金额兜底，超限阻断 */
        private BigDecimal singleMaxAmount;

        /** 限制周期：LIFETIME / DAILY / WEEKLY / MONTHLY / CUSTOM */
        private String limitPeriod;

        /** 限制周期为 CUSTOM 时的窗口开始时间 */
        private LocalDateTime limitStartTime;

        /** 限制周期为 CUSTOM 时的窗口结束时间 */
        private LocalDateTime limitEndTime;

        /** 同周期内，单会员ID最多领取次数 (-1为不限)。这是五个维度里唯一真正生效的 */
        private Integer identifyLimit;

        /** 同周期内，单手机号最多领取次数 (-1为不限)。暂无消费方 */
        private Integer phoneLimit;

        /** 同周期内，单IP地址最多领取次数 (-1为不限)。暂无消费方 */
        private Integer ipLimit;

        /** 同周期内，单设备硬件号最多领取次数 (-1为不限)。暂无消费方 */
        private Integer deviceLimit;

        /** 同周期内，单客户端指纹最多领取次数 (-1为不限)。暂无消费方 */
        private Integer fingerprintLimit;

        /** 互斥规则：互斥的优惠配置ID数组（json 文本）。暂无消费方 */
        private String mutexRule;

        /** 这个类型启不启用，落到 t_promotion_config.status */
        private EnableStatusEnum status;
    }
}
