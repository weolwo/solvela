package solvela.enums;

/**
 * 资产流水的业务类型：{@code t_member_asset_transaction.biz_type}，也是 {@code AssetDebitCmd} / {@code AssetGrantCmd} 的 bizType，
 * 以及成长值流水 {@code t_member_growth_log.biz_type}（从积分入账事件里原样带过来，「算不算成长值」按它判）。
 * 按枚举名存，JSON 也是枚举名。
 *
 * <p>⚠️ 标了 {@link Deprecated} 的三个值<b>只为读老数据</b>：它们写在流水表 DDL 的注释里
 * （「业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST」），现在的代码一处都不写，但生产库里的老流水很可能还是这些值。
 * 新代码不许写它们。
 */
public enum AssetBizTypeEnum {

    /** 发奖提案入账 */
    PROPOSAL_REWARD,

    /** 商城兑换（扣积分、取消退还） */
    MALL_EXCHANGE,

    /** 等级权益领取 */
    GRADE_ENTITLEMENT,

    /** 只为读老数据，新代码不写 */
    @Deprecated
    TASK_PRIZE,

    /** 只为读老数据，新代码不写 */
    @Deprecated
    CONSUME,

    /** 只为读老数据，新代码不写 */
    @Deprecated
    MANUAL_ADJUST,
}
