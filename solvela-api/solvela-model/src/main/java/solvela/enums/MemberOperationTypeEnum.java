package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;


/**
 * 会员<b>受限操作</b>类型，对齐 {@code t_member_operation_limit.operation_type}。
 *
 * <h3>与 t_member.status 的分工</h3>
 * {@code t_member.status = FROZEN} 是<b>账号级</b>封禁：后台人工操作、无到期时间、
 * 封了就整个账号不能用（类似平台封号）。本枚举是<b>功能级</b>限制：风控自动触发、
 * 带到期时间、只挡住列出的那一个操作，其余功能照常。
 *
 * <p>两者在客服话术里必须分清 —— 会员说「我被冻结了」时，客服要先看是哪一种，
 * 否则会出现「解了半天还是进不去」（解了功能限制，账号还封着）。
 *
 * @Date 2026-08-26
 */
@Getter
@AllArgsConstructor
public enum MemberOperationTypeEnum implements BaseEnum {

    /**
     * 密码登录：连续输错密码触发
     */
    LOGIN(1, "登录"),

    /**
     * 修改密码：连续验证旧密码失败触发
     */
    CHANGE_PASSWORD(2, "修改密码"),
    ;

    private final Integer value;

    private final String desc;
}
