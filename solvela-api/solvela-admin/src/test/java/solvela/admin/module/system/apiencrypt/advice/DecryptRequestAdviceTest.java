package solvela.admin.module.system.apiencrypt.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.util.ReflectionTestUtils;
import solvela.admin.module.system.apiencrypt.service.ApiEncryptServiceSmImpl;
import solvela.exception.BusinessException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 请求解密。
 *
 * <h3>🔴 以前的样子</h3>
 * 任何失败都 {@code return inputMessage}，而那个流已经被读空了：下游拿到空请求体，
 * 报「请求参数格式错误」，真正原因只剩一行空消息的 ERROR 日志。
 * 这里把每种失败各自的出口钉住。
 */
@DisplayName("请求解密")
class DecryptRequestAdviceTest {

    private final ApiEncryptServiceSmImpl sm4 = new ApiEncryptServiceSmImpl();

    private final DecryptRequestAdvice advice = newAdvice();

    private DecryptRequestAdvice newAdvice() {
        DecryptRequestAdvice a = new DecryptRequestAdvice();
        ReflectionTestUtils.setField(a, "apiEncryptService", sm4);
        ReflectionTestUtils.setField(a, "objectMapper", JsonMapper.builder().build());
        return a;
    }

    private HttpInputMessage read(String body) {
        return advice.beforeBodyRead(new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8)), null, null, null);
    }

    @Test
    @DisplayName("合法密文 → 下游拿到解密后的明文")
    void 正常解密() throws IOException {
        String plain = "{\"loginName\":\"admin\"}";
        HttpInputMessage out = read("{\"encryptData\":\"" + sm4.encrypt(plain) + "\"}");
        assertEquals(plain, new String(out.getBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("🔴 密文解不开 → 用户级 BusinessException，不是空请求体")
    void 密文解不开() {
        BusinessException e = assertThrows(BusinessException.class, () -> read("{\"encryptData\":\"YWJj\"}"));
        assertEquals("加密数据无法解密", e.getMessage());
    }

    @Test
    @DisplayName("请求体不是 JSON → 400 的 HttpMessageNotReadableException，带原始原因")
    void 不是JSON() {
        HttpMessageNotReadableException e = assertThrows(HttpMessageNotReadableException.class, () -> read("not json"));
        assertEquals("加密请求体读取失败", e.getMessage());
    }

    @Test
    @DisplayName("⚠️ 没带 encryptData → 拒绝，不放行明文（放行等于给加密接口开旁路）")
    void 没带密文() {
        assertThrows(HttpMessageNotReadableException.class, () -> read("{\"loginName\":\"admin\"}"));
        assertThrows(HttpMessageNotReadableException.class, () -> read("{\"encryptData\":\"\"}"));
    }
}
