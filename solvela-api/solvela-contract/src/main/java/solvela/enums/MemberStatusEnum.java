package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会员账号状态，对齐 {@code t_member.status}。
 *
 * <p>C 端登录链路上的第一道闸门：{@code MemberPrincipalLoader} 只放行 {@link #NORMAL}，
 * {@code LoginService} 对 {@link #CANCELLED} 与 {@link #FROZEN} 分别给不同的提示。
 *
 * <p>⚠️ {@link #CANCELLED} 是<b>不可逆</b>的：注销会同时抹掉 PII，后台不提供
 * 「从注销改回正常」的入口。所以管理端的改状态接口只接受 NORMAL / FROZEN 两个目标值。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum MemberStatusEnum implements BaseEnum {

    NORMAL(1, "正常"),

    /**
     * 冻结：风控或违规导致，可由后台改回正常
     */
    FROZEN(2, "冻结"),

    /**
     * 已注销：不可逆终态，PII 已抹除
     */
    CANCELLED(3, "已注销"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}：正常 ↔ 冻结；已注销是终态。
     *
     * <p>注销目前没有任何代码会写入（C 端注销还没做），所以没有到已注销的边 ——
     * 做注销时在这里加边，并配上带旧状态条件的 UPDATE。
     *
     * <p>🔴 闸门在 SQL 里：{@code MemberMapper.xml} 的 {@code markFrozen} / {@code markNormal} 带旧状态条件，
     * 已注销的会员两条都影响 0 行。{@code MemberStatusSqlTest} 断言那些 SQL 与这里一一对应。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(MemberStatusEnum to) {
        return switch (this) {
            case NORMAL -> to == FROZEN;
            case FROZEN -> to == NORMAL;
            case CANCELLED -> false;
        };
    }
}
