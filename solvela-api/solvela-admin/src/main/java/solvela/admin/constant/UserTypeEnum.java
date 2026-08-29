package solvela.admin.constant;

import solvela.enums.BaseEnum;

/**
 * 用户类型。<b>这两个数字是落库的</b>：{@code t_login_log.user_type}、
 * {@code t_operate_log.user_type}、{@code t_login_fail.user_type} 存的就是它，
 * 改动取值等于让存量数据全部错位。
 *
 * <p>住在 admin：产生和读取这三张表的只有管理端。C 端重写后自己管登录态与失败锁定，
 * 不再共用这套枚举 —— 但 {@link #MEMBER} 必须留着，因为历史数据里有 user_type = 2 的行，
 * 删掉枚举常量会让那些行在管理端列表里显示成空白。
 */
public enum UserTypeEnum implements BaseEnum {

    /**
     * 管理端 员工用户
     */
    ADMIN_EMPLOYEE(1, "员工"),

    /**
     * C 端 会员用户。<b>只用于解释存量数据</b>：新的 C 端（solvela-app）不写这三张表。
     */
    MEMBER(2, "会员");

    private final Integer type;

    private final String desc;

    UserTypeEnum(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    @Override
    public Integer getValue() {
        return type;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
