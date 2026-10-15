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

    /**
     * 资产出口：加收货地址、兑换、充话费。<b>用户点了「这不是我」时冻结，重置密码后恢复。</b>
     *
     * <h3>为什么冻的是「出口」而不是整个账号</h3>
     * 本站盗号的变现路径只有一条：<b>指定一个自己的收件方再把东西拿走</b>。
     * 冻住这一步，账号其余功能照常用 —— 而冻整个账号的代价全落在
     * <b>主人</b>身上（他正是那个来举报的人）。
     *
     * <h3>🔴 它没有「到期」这个概念</h3>
     * {@code expire_time} 那一列是 NOT NULL，所以落库时填一个很远的日期，
     * 但<b>正确的出口只有「重置密码」</b>（{@code unlock_type = 2}）——
     * 等它自己过期等于「盗号者等几天就能继续」。
     * 这也是本枚举里唯一一个不靠时间自愈的类型。
     */
    ASSET_OUT(3, "资产出口"),
    ;

    private final Integer value;

    private final String desc;
}
