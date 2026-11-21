package solvela.enums;

/**
 * 资产从哪来：券的 {@code t_member_coupon.source_type} 与履约单的 {@code t_physical_delivery.source_type}，
 * 也是 {@code AssetGrantCmd.sourceType}（同一个字段既落券、也落履约单）。按枚举名存，JSON 也是枚举名。
 *
 * <p>⚠️ 标了 {@link Deprecated} 的值<b>只为读老数据</b>：REISSUE 来自一次性迁移，其余三个：它们写在券表 DDL 的注释里
 * （「来源：DRAW, TASK, MANUAL_SEND」），现在的代码一处都不写，但生产库里的老券很可能还是这些值 ——
 * 不收进来，读到那一行就直接报错。新代码不许写它们。
 */
public enum AssetSourceEnum {

    /** 发奖提案（抽奖、任务、彩票中奖后经提案下发） */
    PROPOSAL,

    /** 商城兑换 */
    MALL,

    /** 等级权益领取 */
    GRADE_ENTITLEMENT,

    /** 人工发放（后台人工发券） */
    MANUAL,

    /**
     * 2026-09-15 存量券作废重发时写入（248 张，source_biz_id = 旧券 id），只有那次一次性迁移写过。
     * 来源：docs/业务/商城与券/优惠券使用闭环-实现技术方案.md「作废 + 重发」。新代码不写。
     */
    @Deprecated
    REISSUE,

    /** 只为读老数据，新代码不写 */
    @Deprecated
    DRAW,

    /** 只为读老数据，新代码不写 */
    @Deprecated
    TASK,

    /** 只为读老数据，新代码不写 */
    @Deprecated
    MANUAL_SEND,
}
