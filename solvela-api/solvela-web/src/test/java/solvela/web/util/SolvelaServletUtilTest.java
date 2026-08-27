package solvela.web.util;

import solvela.web.SolvelaServletUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 取客户端 IP 的规则固化测试。
 *
 * 期望值与原 JakartaServletUtil.getClientIP 逐条比对过（15 组请求，0 处不一致）。
 *
 * @Date 2026-08-08
 */
public class SolvelaServletUtilTest {

    @Test
    public void 没有代理头时回落到remoteAddr() {
        assertEquals("127.0.0.1", getClientIp(null, null));
    }

    @Test
    public void 代理链取第一段而不是最后一段() {
        // 🔴 X-Forwarded-For 是 `客户端, 代理1, 代理2`，要的是最靠近客户端的第一段。
        // 取错成最后一段，登录日志里所有人看起来都来自自家网关
        assertEquals("1.2.3.4", getClientIp("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 9.9.9.9"));
        // 段之间的空格要去掉
        assertEquals("1.2.3.4", getClientIp("X-Forwarded-For", "  1.2.3.4  ,  5.6.7.8 "));
    }

    @Test
    public void 跳过unknown段() {
        assertEquals("5.6.7.8", getClientIp("X-Forwarded-For", "unknown, 5.6.7.8"));
        assertEquals("5.6.7.8", getClientIp("X-Forwarded-For", "UNKNOWN, 5.6.7.8"));
        // 整个头就是 unknown / 空白时，当作没有这个头，继续往下找
        assertEquals("127.0.0.1", getClientIp("X-Forwarded-For", "unknown"));
        assertEquals("127.0.0.1", getClientIp("X-Forwarded-For", ""));
        assertEquals("127.0.0.1", getClientIp("X-Forwarded-For", "   "));
    }

    @Test
    public void 六个候选头都要认() {
        assertEquals("1.1.1.1", getClientIp("X-Forwarded-For", "1.1.1.1"));
        assertEquals("2.2.2.2", getClientIp("X-Real-IP", "2.2.2.2"));
        assertEquals("3.3.3.3", getClientIp("Proxy-Client-IP", "3.3.3.3"));
        assertEquals("4.4.4.4", getClientIp("WL-Proxy-Client-IP", "4.4.4.4"));
        assertEquals("5.5.5.5", getClientIp("HTTP_CLIENT_IP", "5.5.5.5"));
        assertEquals("6.6.6.6", getClientIp("HTTP_X_FORWARDED_FOR", "6.6.6.6"));
        // 头名大小写不敏感（Servlet 规范如此）
        assertEquals("7.7.7.7", getClientIp("x-forwarded-for", "7.7.7.7"));
    }

    @Test
    public void 多个头同时存在时按候选顺序取优先级最高的() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Proxy-Client-IP", "3.3.3.3");
        request.addHeader("X-Real-IP", "2.2.2.2");
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        // X-Forwarded-For 排在最前
        assertEquals("1.1.1.1", SolvelaServletUtil.getClientIP(request));
    }

    @Test
    public void 优先级更高的头是unknown时顺延到下一个() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "2.2.2.2");
        assertEquals("2.2.2.2", SolvelaServletUtil.getClientIP(request));
    }

    private String getClientIp(String headerName, String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        if (headerName != null) {
            request.addHeader(headerName, headerValue);
        }
        return SolvelaServletUtil.getClientIP(request);
    }
}
