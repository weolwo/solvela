package solvela.admin.module.system.apiencrypt.advice;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import solvela.admin.module.system.apiencrypt.annotation.ApiEncrypt;
import solvela.admin.module.system.apiencrypt.service.ApiEncryptService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 给标了 {@link ApiEncrypt} 的接口加密响应体。
 *
 * <h3>加密标记从响应体挪到了响应头</h3>
 * 上一版是往 {@code ResponseDTO.dataType} 里写 10 表示「这段 data 是密文」。
 * 那个字段有两个问题：它是<b>传输信封里的一个 UI 概念</b>；以及它要求响应体
 * 必须是 {@code ResponseDTO} —— 于是这个 advice 的泛型被钉死在 {@code ResponseDTO<Object>} 上，
 * 加密能力和信封绑成了一体。
 *
 * <p>现在：整个响应体就是那串密文，是不是密文看 {@code X-Encrypted: 1} 响应头。
 * 头是 HTTP 自己表达元信息的地方，客户端在拦截器里读一次就够，
 * 也不需要每个接口的返回类型都长成同一个样子。
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/10/24 09:52:58
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>，Since 2012
 */
@Slf4j
@ControllerAdvice
public class EncryptResponseAdvice implements ResponseBodyAdvice<Object> {

    /** 响应体是否为密文。前端据此决定要不要先解密再当 JSON 解析 */
    public static final String ENCRYPTED_HEADER = "X-Encrypted";

    @Resource
    private ApiEncryptService apiEncryptService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.hasMethodAnnotation(ApiEncrypt.class)
                || returnType.getContainingClass().isAnnotationPresent(ApiEncrypt.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            // 没有内容就没有要加密的东西，也不要打标记 —— 打了会让前端去解密一个 null
            return null;
        }
        try {
            String encrypted = apiEncryptService.encrypt(objectMapper.writeValueAsString(body));
            response.getHeaders().add(ENCRYPTED_HEADER, "1");
            return encrypted;
        } catch (JacksonException e) {
            // 加密不了就不能把明文发出去 —— 那正是这个注解要防的事
            throw new IllegalStateException("响应体序列化失败，无法加密", e);
        }
    }
}
