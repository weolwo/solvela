package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 登录结果。**员工端与会员端共用同一套取值。**
 *
 * <p>对齐 {@code t_login_log.login_result} 与 {@code t_member_login_log.status}。
 *
 * <h3>为什么搬到 solvela-model</h3>
 * 原先它在 solvela-admin 里，会员端够不着，于是 {@code t_member_login_log.status}
 * 只能自己在 {@code MemberConst} 里另写三个 int 常量 —— 而且<b>把 0 和 1 写反了</b>：
 * 员工端 0 是成功，会员端 0 是失败。两张表都叫「登录日志」、字段都是「状态」，
 * 语义却相反，DDL 注释里不得不专门写一行 ⚠️ 来提醒。
 *
 * <p>2026-08-29 统一成本枚举：会员端那张表当时还是零行，改口径的成本为零，
 * 过了这个窗口就得做数据迁移了。
 *
 * <p>⚠️ 字段必须叫 value 而不是 code/type：{@link BaseEnum#getValue()} 是 @CheckEnum
 * 校验器建立合法值白名单的唯一来源，得让 Lombok 的 @Getter 直接生成出 getValue() 才对得上。
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022/07/22 19:46:23
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Getter
@AllArgsConstructor
public enum LoginLogResultEnum implements BaseEnum {

    LOGIN_SUCCESS(0, "登录成功"),

    LOGIN_FAIL(1, "登录失败"),

    LOGIN_OUT(2, "退出登录"),
    ;

    private final Integer value;

    private final String desc;
}
