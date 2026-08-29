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
 * <p>{@code MemberWallet.checkAvailable()} 判的就是 {@link #NORMAL}，
 * 不是这个状态就抛 ACCOUNT_FROZEN。
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
}
