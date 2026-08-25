package sa.base.common.enumeration;

/**
 * 用户类型
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2022/10/19 21:46:24
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
public enum UserTypeEnum implements BaseEnum {

    /**
     * 管理端 员工用户
     */
    ADMIN_EMPLOYEE(1, "员工"),

    /**
     * C 端 会员用户（solarx-app）
     *
     * <p>这个值同时是三处的组成部分，改动它等于让存量数据全部错位：
     * <ul>
     *   <li>sa-token 的 loginId 前缀 —— {@code 2:会员号}，见 {@code MemberLoginService}；</li>
     *   <li>{@code t_password_log.user_type} —— 会员改密历史按它归属；</li>
     *   <li>{@code t_login_fail.user_type} —— 等保登录失败锁定按它计数。</li>
     * </ul>
     */
    MEMBER(2, "会员");

    private Integer type;

    private String desc;

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
