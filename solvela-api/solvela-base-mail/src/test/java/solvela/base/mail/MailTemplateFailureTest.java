package solvela.base.mail;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板渲染失败。
 *
 * <h3>🔴 以前的样子</h3>
 * freemarker 渲染出错时退回空串，再被「邮件内容不能为空」拦下，最后包成
 * {@code BusinessException("邮件发送失败")} —— 默认是用户级 PARAM_ERROR，而且没挂 cause。
 * 运维只看得到「发送失败」，看不到是模板写错了，告警曲线上还算成了用户的错。
 */
@DisplayName("邮件模板渲染失败")
class MailTemplateFailureTest {

    @Test
    @DisplayName("🔴 模板语法错 → 抛系统级异常并带上模板编码与原始原因，不发信")
    void 模板写错要原样报出来() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailTemplateDao dao = mock(MailTemplateDao.class);
        MailTemplateEntity entity = new MailTemplateEntity();
        entity.setTemplateSubject("验证码");
        entity.setTemplateType(MailTemplateTypeEnum.FREEMARKER.name());
        entity.setTemplateContent("<p>${code</p>");
        entity.setDisableFlag(false);
        when(dao.selectById(any())).thenReturn(entity);

        MailService service = new MailService();
        ReflectionTestUtils.setField(service, "javaMailSender", sender);
        ReflectionTestUtils.setField(service, "mailTemplateDao", dao);

        // 断言的是 IllegalStateException 而不是 BusinessException：后者默认用户级，系统故障不能算成用户的错
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.sendMail(MailTemplateCodeEnum.LOGIN_VERIFICATION_CODE,
                        Map.of("code", "123456"), List.of("a@example.com")));

        assertTrue(e.getMessage().contains("LOGIN_VERIFICATION_CODE"), "报错要说清是哪个模板：" + e.getMessage());
        assertNotNull(e.getCause(), "freemarker 的原始异常要挂在 cause 上");
        verify(sender, never()).send(any(MimeMessage.class));
    }
}
