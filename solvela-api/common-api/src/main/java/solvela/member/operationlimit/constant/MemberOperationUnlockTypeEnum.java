package solvela.member.operationlimit.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.enumeration.BaseEnum;

/**
 * 解冻方式，对齐 {@code t_member_operation_limit.unlock_type}。
 *
 * <p>这一列的价值全在事后追溯：出现「会员投诉被限制」时，
 * 要能一眼看出他是<b>自己等到期</b>、<b>自己重置密码解开</b>，还是<b>客服捞的</b>——
 * 最后一种要能追到人，所以 {@link #MANUAL} 必须同时写 {@code unlock_operator}。
 *
 * @Date 2026-08-26
 */
@Getter
@AllArgsConstructor
public enum MemberOperationUnlockTypeEnum implements BaseEnum {

    /**
     * 自动到期：由定时任务回写，或首次命中时惰性回写
     */
    AUTO_EXPIRE(1, "自动到期"),

    /**
     * 重置密码：会员走短信验证码重置密码，视为已证明身份，当场解开登录限制
     */
    RESET_PASSWORD(2, "重置密码"),

    /**
     * 人工：客服后台解冻，必须记录操作人
     */
    MANUAL(3, "人工解冻"),
    ;

    private final Integer value;

    private final String desc;
}
