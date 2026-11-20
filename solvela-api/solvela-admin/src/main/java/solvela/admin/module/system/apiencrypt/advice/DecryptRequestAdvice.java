package solvela.admin.module.system.apiencrypt.advice;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import solvela.base.util.SolvelaStringUtil;
import solvela.admin.module.system.apiencrypt.annotation.ApiDecrypt;
import solvela.admin.module.system.apiencrypt.domain.ApiEncryptForm;
import solvela.admin.module.system.apiencrypt.service.ApiEncryptService;
import org.apache.commons.io.IOUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * 解密
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/10/21 11:41:46
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>，Since 2012
 */

@ControllerAdvice
public class DecryptRequestAdvice extends RequestBodyAdviceAdapter {

    private static final String ENCODING = "UTF-8";

    @Resource
    private ApiEncryptService apiEncryptService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.hasMethodAnnotation(ApiDecrypt.class) || methodParameter.hasParameterAnnotation(ApiDecrypt.class) || methodParameter.getContainingClass().isAnnotationPresent(ApiDecrypt.class);
    }

    /**
     * 🔴 以前这里 catch 一切异常后 {@code return inputMessage} —— 而那个流已经被上面读空了，
     * 下游拿到空请求体，报的是「请求参数格式错误」，真正的原因只剩一行空消息的 ERROR 日志。
     * 现在各类失败按自己的样子往外抛：读不出 / 不是 JSON / 没带密文 → 400（全局处理器接
     * {@link HttpMessageNotReadableException}），密文解不开 → 解密方法自己抛的用户级 BusinessException。
     *
     * <p>⚠️ 没带 encryptData 时<b>拒绝而不是放行明文</b>。旧代码看起来是想放行，但因为流已读空，
     * 实际效果一直是拒绝；真要放行明文，等于给加密接口开一条旁路，那是要单独拍板的安全决定。
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        ApiEncryptForm apiEncryptForm;
        try {
            String bodyStr = IOUtils.toString(inputMessage.getBody(), ENCODING);
            apiEncryptForm = objectMapper.readValue(bodyStr, ApiEncryptForm.class);
        } catch (IOException | JacksonException e) {
            throw new HttpMessageNotReadableException("加密请求体读取失败", e, inputMessage);
        }
        if (SolvelaStringUtil.isEmpty(apiEncryptForm.getEncryptData())) {
            throw new HttpMessageNotReadableException("该接口要求加密请求体，缺少 encryptData", inputMessage);
        }
        String decrypt = apiEncryptService.decrypt(apiEncryptForm.getEncryptData());
        return new DecryptHttpInputMessage(inputMessage.getHeaders(), IOUtils.toInputStream(decrypt, ENCODING));
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    static class DecryptHttpInputMessage implements HttpInputMessage {
        private final HttpHeaders headers;

        private final InputStream body;

        public DecryptHttpInputMessage(HttpHeaders headers, InputStream body) {
            this.headers = headers;
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

}
