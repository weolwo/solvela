package solvela.member.operationlimit.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.enumeration.BaseEnum;

/**
 * 会员操作限制的状态，对齐 {@code t_member_operation_limit.status}。
 *
 * <p>⚠️ {@link #LOCKED} <b>不等于</b>「此刻真的被挡着」：到期时间过了但还没被回写的行，
 * 状态仍是 LOCKED。判断「现在能不能操作」一律用
 * {@code status = 0 AND expire_time > now()}，不要只看 status ——
 * 详见 {@code MemberOperationLimitService#isLocked}。
 *
 * @Date 2026-08-26
 */
@Getter
@AllArgsConstructor
public enum MemberOperationLimitStatusEnum implements BaseEnum {

    /**
     * 冻结中
     */
    LOCKED(0, "冻结中"),

    /**
     * 已解冻（终态，解冻方式见 unlock_type）
     */
    UNLOCKED(1, "已解冻"),
    ;

    private final Integer value;

    private final String desc;
}
