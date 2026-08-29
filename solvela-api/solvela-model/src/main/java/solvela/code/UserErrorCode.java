package solvela.code;

import lombok.Getter;

/**
 * 用户级别的错误码（用户引起的错误返回码，可以不用关注）
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2021/09/21 22:12:27
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Getter
public enum UserErrorCode implements ErrorCode {

    PARAM_ERROR("参数错误"),

    DATA_NOT_EXIST("左翻右翻，数据竟然找不到了~"),

    ALREADY_EXIST("数据已存在了呀~"),

    REPEAT_SUBMIT("亲~您操作的太快了，请稍等下再操作~"),

    NO_PERMISSION("对不起，您没有权限访问此内容哦~"),

    DEVELOPING("系統正在紧急开发中，敬请期待~"),

    LOGIN_STATE_INVALID("您还未登录或登录失效，请重新登录！"),

    USER_STATUS_ERROR("用户状态异常"),

    FORM_REPEAT_SUBMIT("请勿重复提交"),

    LOGIN_FAIL_LOCK("登录连续失败已经被锁定，无法登录"),
    LOGIN_FAIL_WILL_LOCK("登录连续失败将会锁定提醒"),

    LOGIN_ACTIVE_TIMEOUT("长时间未操作系统，需要重新登录"),

    STATUS_ERROR("状态异常"),

    ACCOUNT_FROZEN("用户账户异常，已被冻结"),
    ;

    private final String msg;

    UserErrorCode(String msg) {
        this.msg = msg;
    }
}
