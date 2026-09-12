package solvela.base.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发件地址。
 *
 * <h3>🔴 From 不等于 SMTP 用户名 —— 这个巧合骗了很久</h3>
 * 163 / QQ / Gmail 的 SMTP 用户名<b>恰好</b>就是邮箱地址，
 * 所以「拿 username 当 From」在这些服务商上一直是对的。
 * 但那是巧合：Resend 的用户名是字面量 {@code resend}，
 * AWS SES 是一串 IAM 凭据 ID。
 *
 * <p>而配错的表现很不友好：注册页点「获取验证码」，转圈，失败，
 * 服务端日志里是一句 SMTP 协议错误 —— 没人会想到是 From 写错了。
 * 所以在启动时拦。
 */
@DisplayName("发件地址")
class MailFromAddressTest {

    @Test
    @DisplayName("🔴 配了 SMTP 却把非邮箱当 From（Resend 的典型踩法）→ 启动就拦下")
    void Resend的用户名不能当发件地址() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> MailService.validateFrom("resend", "resend"));

        assertTrue(e.getMessage().contains("solvela.mail.from"),
                "报错要指名道姓说该配哪个配置项，而不是只说『地址不合法』");
    }

    @Test
    @DisplayName("没配 SMTP → 不拦。发信功能没开是合法状态，不该让整个后台起不来")
    void 没配SMTP时不拦() {
        assertDoesNotThrow(() -> MailService.validateFrom("", ""));
        assertDoesNotThrow(() -> MailService.validateFrom(null, null));
    }

    @Test
    @DisplayName("正常地址通过，带显示名的也通过")
    void 正常地址通过() {
        assertDoesNotThrow(() -> MailService.validateFrom("resend", "noreply@taozicn.me"));
        assertDoesNotThrow(() -> MailService.validateFrom("resend", "Solvela <noreply@taozicn.me>"));
    }

    @Test
    @DisplayName("163 那种「用户名就是邮箱」的老配置，不配 from 也照常工作")
    void 老配置不受影响() {
        // 默认值是 ${solvela.mail.from:${spring.mail.username:}} —— 回落到用户名。
        // 这条测试锁的是「这次改动没有要求存量部署去加一行配置」。
        assertDoesNotThrow(() -> MailService.validateFrom("lab1024@163.com", "lab1024@163.com"));
    }
}
