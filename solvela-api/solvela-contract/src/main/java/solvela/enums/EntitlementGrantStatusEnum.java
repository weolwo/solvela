package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 等级权益发放记录的状态，对齐 {@code t_grade_entitlement_grant.status}。
 *
 * <p>🔴 「待领取」和「已过期」必须分开，不能靠 {@code expire_time < now} 现算：
 * 那样「这个月有多少权益没人领」这个数只能靠扫全表算，
 * 而它恰恰是运营最常问的那个数。
 *
 * <h3>🔴 状态机的规则在这里，闸门在 SQL 里</h3>
 * {@link #canTransitTo} 是唯一一份「谁能变成谁」的声明；真正执行迁移的是
 * {@code GradeEntitlementGrantMapper.xml} 里带旧状态条件的 UPDATE（领取 / 过期），
 * 不要改成先读出实体、在内存里判断再写回 —— 两个请求同时点，会领到两份。
 * 那几条 SQL 里的状态值引用本枚举，不写数字；{@code EntitlementGrantStatusSqlTest}
 * 渲染出真正发给数据库的 SQL，断言迁移与 {@link #canTransitTo} 一条不多、一条不少。
 * 约定见 {@code docs/架构/领域实体轻充血-架构设计文档.md}。
 */
@Getter
@AllArgsConstructor
public enum EntitlementGrantStatusEnum implements BaseEnum {

    PENDING(0, "待领取"),

    CLAIMED(1, "已领取"),

    /** 已过期，再也领不了了 */
    EXPIRED(2, "已过期"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}。已领取、已过期都是终态。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(EntitlementGrantStatusEnum to) {
        return switch (this) {
            case PENDING -> to == CLAIMED || to == EXPIRED;
            case CLAIMED, EXPIRED -> false;
        };
    }
}
