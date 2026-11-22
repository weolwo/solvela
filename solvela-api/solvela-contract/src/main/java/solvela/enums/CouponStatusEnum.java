package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会员券状态，对齐 {@code t_member_coupon.status}。
 *
 * <p>⚠️ {@link #EXPIRED} 与 {@link #VOIDED} 要分开：前者是时间到了自然失效，
 * 后者是运营主动作废（发错了、活动取消）。对用户都是「用不了」，对运营是两回事 ——
 * 作废量突然变大意味着有人在批量撤券，那是要问一句的。
 *
 * <h3>🔴 状态机的规则在这里，闸门在 SQL 里</h3>
 * {@link #canTransitTo} 是唯一一份「谁能变成谁」的声明；真正执行迁移的是
 * {@code MemberCouponMapper.xml} 里带旧状态条件的 UPDATE（锁定 / 确认 / 释放 / 过期），
 * 不要改成先读出实体、在内存里判断再写回 —— 两笔订单会同时看到「未使用」，一张券被用两次。
 * 那几条 SQL 里的状态值引用本枚举，不写数字；{@code MemberCouponStatusSqlTest}
 * 渲染出真正发给数据库的 SQL，断言 DAO 的迁移与 {@link #canTransitTo} 一条不多、一条不少。
 * 约定见 {@code docs/架构/领域实体轻充血-架构设计文档.md}。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum CouponStatusEnum implements BaseEnum {

    UNUSED(0, "未使用"),

    USED(1, "已使用"),

    /**
     * 已过期：时间到了自然失效
     */
    EXPIRED(2, "已过期"),

    /**
     * 已作废：运营主动撤销
     */
    VOIDED(3, "已作废"),

    /**
     * 锁定中：用户提交了订单但还没支付 / 履约完成。
     *
     * <h3>🔴 为什么必须有这一档</h3>
     * 核销不能一步到位。用户下单用了一张券，订单后来失败或超时取消 ——
     * 一步核销的话券就白没了。
     *
     * <p>这和「商城超时取消要退积分」是<b>完全同构</b>的问题：积分有
     * {@code debit} / {@code refund} 一对，券就必须有锁定 / 释放一对。
     *
     * <p>配套的两列在 {@code t_member_coupon} 上：{@code locked_biz_id}
     *（被哪一笔锁的，同时是幂等依据）与 {@code locked_time}
     *（兜底 job 按它判超时释放）。
     */
    LOCKED(4, "锁定中"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}。
     *
     * <p>⚠️ 已作废(3) 至今没有任何入边：运营作废券的功能还没实现。
     * 实现时先在这里加边，{@code MemberCouponStatusSqlTest} 会逼着 DAO 补上对应的闸门 SQL。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(CouponStatusEnum to) {
        return switch (this) {
            case UNUSED -> to == LOCKED || to == EXPIRED;
            case LOCKED -> to == USED || to == UNUSED;
            case USED, EXPIRED, VOIDED -> false;
        };
    }
}
