package solvela.base.mail;


import solvela.exception.BusinessException;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.util.SolvelaRandomUtil;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import solvela.base.domain.SystemEnvironment;
import solvela.base.util.SolvelaTemplateUtil;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 *
 * 发送邮件：<br/>
 * 1、支持直接发送 <br/>
 * 2、支持使用邮件模板发送
 *
 * @Author 1024创新实验室-创始人兼主任:卓大
 * @Date 2024/8/5
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a> ，Since 2012
 */
@Slf4j
@Component
public class MailService {

    @Resource
    private JavaMailSender javaMailSender;

    @Resource
    private MailTemplateDao mailTemplateDao;

    @Resource
    private SystemEnvironment systemEnvironment;

    /**
     * 发件地址（信头里的 From）。
     *
     * <h3>🔴 它【不等于】SMTP 用户名，这一点踩过</h3>
     * 原先这里直接读 {@code spring.mail.username}。163 / QQ / Gmail 的用户名
     * 恰好就是邮箱地址，所以一直没事 —— 但那是巧合，不是规律。
     *
     * <p>Resend 的 SMTP 用户名是<b>字面量 {@code resend}</b>，密码才是 API Key。
     * 照旧读 username 的话，{@code setFrom("resend")} 会直接失败，
     * 而这要等到第一封验证码邮件才暴露。AWS SES 也是同一形状（用户名是一串
     * IAM 凭据 ID）。
     *
     * <p>所以拆成独立配置项，缺省仍回落到 username —— 163 那类配置一行都不用改。
     *
     * <p>可以带显示名：{@code Solvela <noreply@example.com>}。
     * ⚠️ 地址的域名必须是在服务商那里<b>验证过</b>的，否则会被拒（Resend 是 403）。
     */
    @Value("${solvela.mail.from:${spring.mail.username:}}")
    private String clientMail;

    /**
     * 配了 SMTP 却给了个不是邮箱的 From —— 启动就拦下来。
     *
     * <p>不拦的话，表现是「注册页点了获取验证码，转圈，然后失败」，
     * 而服务端日志里是一句 SMTP 协议错误，没人会想到是 From 写错了。
     *
     * <p>只在「已经配了 SMTP 用户名」时才检查：没配 SMTP 是合法状态
     * （发信功能没开），那种情况下 From 为空是正常的，不该拦启动。
     */
    @jakarta.annotation.PostConstruct
    void checkFromAddress() {
        validateFrom(smtpUsername, clientMail);
    }

    /** 抽成静态方法只为了能直接测 —— 这个类是字段注入的，构造不出来。 */
    static void validateFrom(String smtpUsername, String from) {
        // 没配 SMTP 是合法状态（发信功能没开），那时 From 为空是正常的，不该拦启动
        if (SolvelaStringUtil.isBlank(smtpUsername)) {
            return;
        }
        if (from == null || !from.contains("@")) {
            throw new IllegalStateException(
                    "发件地址不是一个邮箱：solvela.mail.from=\"" + from + "\"。"
                            + "它默认回落到 spring.mail.username，而有些服务商的 SMTP 用户名"
                            + "不是邮箱地址（Resend 是字面量 resend，AWS SES 是一串凭据 ID）。"
                            + "请显式配置 solvela.mail.from，用一个在服务商那里验证过的域名下的地址。");
        }
    }

    @Value("${spring.mail.username:}")
    private String smtpUsername;


    /**
     * 使用模板发送邮件
     */
    public void sendMail(MailTemplateCodeEnum templateCode, Map<String, Object> templateParamsMap, List<String> receiverUserList, List<File> fileList) {

        MailTemplateEntity mailTemplateEntity = mailTemplateDao.selectById(templateCode.name().toLowerCase());
        if (mailTemplateEntity == null) {
            throw new BusinessException("模版不存在");
        }

        if (mailTemplateEntity.getDisableFlag()) {
            throw new BusinessException("模版已禁用，无法发送");
        }

        String content = null;
        if (MailTemplateTypeEnum.FREEMARKER.name().equalsIgnoreCase(mailTemplateEntity.getTemplateType().trim())) {
            content = freemarkerResolverContent(mailTemplateEntity.getTemplateContent(), templateParamsMap);
        } else if (MailTemplateTypeEnum.STRING.name().equalsIgnoreCase(mailTemplateEntity.getTemplateType().trim())) {
            content = stringResolverContent(mailTemplateEntity.getTemplateContent(), templateParamsMap);
        } else {
            throw new BusinessException("模版类型不存在");
        }

        try {

            this.sendMail(mailTemplateEntity.getTemplateSubject(), content, fileList, receiverUserList, true);

        } catch (Throwable e) {
            log.error("邮件发送失败", e);
            throw new BusinessException("邮件发送失败");
        }
    }

    /**
     * 使用模板发送邮件
     */
    public void sendMail(MailTemplateCodeEnum templateCode, Map<String, Object> templateParamsMap, List<String> receiverUserList) {
        this.sendMail(templateCode, templateParamsMap, receiverUserList, null);
    }


    /**
     * 发送邮件
     *
     * @param subject          主题
     * @param content          内容
     * @param fileList         文件
     * @param receiverUserList 接收方
     * @throws MessagingException
     */
    public void sendMail(String subject, String content, List<File> fileList, List<String> receiverUserList, boolean isHtml) throws MessagingException {

        if (SolvelaCollectionUtil.isEmpty(receiverUserList)) {
            throw new RuntimeException("接收方不能为空");
        }

        if (StringUtils.isBlank(content)) {
            throw new RuntimeException("邮件内容不能为空");
        }

        if (!systemEnvironment.isProd()) {
            subject = "(测试)" + subject;
        }

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        //是否为多文件上传
        boolean multiparty = !SolvelaCollectionUtil.isEmpty(fileList);
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, multiparty);
        helper.setFrom(clientMail);
        helper.setTo(receiverUserList.toArray(new String[0]));
        helper.setSubject(subject);
        //发送html格式
        helper.setText(content, isHtml);

        //附件
        if (multiparty) {
            for (File file : fileList) {
                helper.addAttachment(file.getName(), file);
            }
        }
        javaMailSender.send(mimeMessage);
    }

    /**
     * 使用字符串生成最终内容
     */
    private String stringResolverContent(String stringTemplate, Map<String, Object> templateParamsMap) {
        String contractHtml = SolvelaTemplateUtil.render(stringTemplate, templateParamsMap);
        Document doc = Jsoup.parse(contractHtml);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        return doc.outerHtml();
    }


    /**
     * 使用 freemarker 生成最终内容
     */
    private String freemarkerResolverContent(String htmlTemplate, Map<String, Object> templateParamsMap) {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_23);
        StringTemplateLoader stringLoader = new StringTemplateLoader();
        String templateName = SolvelaRandomUtil.simpleUuid();
        stringLoader.putTemplate(templateName, htmlTemplate);
        configuration.setTemplateLoader(stringLoader);
        try {
            Template template = configuration.getTemplate(templateName, "utf-8");
            Writer out = new StringWriter(2048);
            template.process(templateParamsMap, out);
            return out.toString();
        } catch (Throwable e) {
            log.error("freemarkerResolverContent error: ", e);
        }
        return "";
    }
}
