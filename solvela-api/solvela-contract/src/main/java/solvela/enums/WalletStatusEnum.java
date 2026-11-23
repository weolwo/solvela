package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会员钱包状态，对齐 {@code t_member_wallet.status}。
 *
 * <p>⚠️ 取值方向与「本项目 status 列 1 = 正常/启用」的通行口径一致，
 * 但与 {@code t_member.status}（1-正常, 2-冻结, 3-已注销）<b>不是同一套字典</b> ——
 * 那边冻结是 2，这边冻结是 0。两列都叫「状态」、都跟会员有关，别互相照抄。
 *
 * <p>{@code MemberWallet.checkAvailable()} 判的是 {@link #allowsBalanceChange}，
 * 不满足就抛 ACCOUNT_FROZEN。
 *
 * <p>⚠️ 目前没有任何代码会改钱包状态：建钱包一律落正常，冻结 / 解冻只能直接改库。
 * 所以这里没有 {@code canTransitTo} —— 状态机的边要与带旧状态条件的 UPDATE 一一对应，没有 UPDATE 就不该有边。
 * 将来加冻结 / 解冻功能时，按 {@code docs/架构/领域实体轻充血-架构设计文档.md} 补上两者。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum WalletStatusEnum implements BaseEnum {

    FROZEN(0, "冻结"),

    NORMAL(1, "正常"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 这个状态下能不能动余额（加、扣、退）。
     *
     * <p>🔴 守它的有两处，同一条规则：{@code MemberWallet#checkAvailable}（读出来先判），
     * {@code MemberWalletMapper.xml} 的加减余额 UPDATE（WHERE 里再判一次）。
     * 冻结是直接改库、不动 version，只靠前者的话，读完到写之间被冻结的钱包照样能扣。
     * {@code MemberWalletStatusSqlTest} 断言两处口径一致。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean allowsBalanceChange() {
        return switch (this) {
            case NORMAL -> true;
            case FROZEN -> false;
        };
    }
}
