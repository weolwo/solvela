package solvela.app.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下游（app-biz）不可达时回 503，其余 I/O 失败仍是 500。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>连接被拒 / 连接超时 / 域名解析不到 → 503 + Retry-After（发版重启那几秒的真实表现）；</li>
 *   <li>🔴 读超时 → 仍是 500：请求可能已经执行完了，说「稍后重试」会诱导用户重复提交；</li>
 *   <li>message 永远是固定文案，不透出异常原文。</li>
 * </ul>
 */
class DownstreamUnavailableTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private static ResourceAccessException io(IOException cause) {
        return new ResourceAccessException("I/O error on GET request for \"http://app-biz:1026/internal/x\"", cause);
    }

    private ResponseEntity<ApiErrorResponse> handle(ResourceAccessException e) {
        return handler.handleDownstreamIo(e, new MockHttpServletRequest("GET", "/activity"));
    }

    @Test
    @DisplayName("连接被拒（app-biz 正在重启）→ 503 + Retry-After，固定文案")
    void 连接被拒回503() {
        ResponseEntity<ApiErrorResponse> resp = handle(io(new ConnectException("Connection refused")));

        assertEquals(503, resp.getStatusCode().value());
        assertEquals("5", resp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("SERVICE_UNAVAILABLE", resp.getBody().code());
        assertEquals(ApiErrors.SERVICE_UNAVAILABLE.defaultMessage(), resp.getBody().message());
    }

    @Test
    @DisplayName("连接超时、域名解析不到 → 都算没送到")
    void 连接超时与解析失败算没送到() {
        assertTrue(ApiExceptionHandler.isNotDelivered(io(new SocketTimeoutException("Connect timed out"))));
        assertTrue(ApiExceptionHandler.isNotDelivered(io(new UnknownHostException("app-biz"))));
    }

    @Test
    @DisplayName("🔴 读超时 → 仍是 500：请求可能已经执行完了")
    void 读超时仍是500() {
        ResourceAccessException e = io(new SocketTimeoutException("Read timed out"));

        assertFalse(ApiExceptionHandler.isNotDelivered(e));
        ResponseEntity<ApiErrorResponse> resp = handle(e);
        assertEquals(500, resp.getStatusCode().value());
        assertEquals("INTERNAL", resp.getBody().code());
    }
}
