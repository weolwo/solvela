package solvela.app.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import solvela.apptest.stub.CookieSessionStub;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CSP 违规上报接口（nginx Report-Only 策略的 report-uri）。
 *
 * <p>{@code @Import(CookieSessionStub)} 只是为了与 {@link CookieSessionTest} 共用同一个 Spring 上下文
 * （配置相同即复用），本类并不用到桩里的任何东西。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CookieSessionStub.class)
@TestPropertySource(properties = {
        // 本类测的不是滑块：发码、密码登录的滑块由 CaptchaFlowTest 负责
        "solvela.app.captcha.send-code=false",
        "solvela.app.captcha.password-login=false",
})
class CspReportTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String REPORT = """
            {"csp-report":{"document-uri":"https://app.example/","violated-directive":"script-src-elem",
            "blocked-uri":"inline","effective-directive":"script-src-elem"}}""";

    @LocalServerPort
    private int port;

    private HttpResponse<String> send(String contentType, String secFetchSite) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/csp-report"))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(REPORT));
        if (secFetchSite != null) {
            builder.header("Sec-Fetch-Site", secFetchSite);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("浏览器的 application/csp-report 能收下：匿名、无设备、同源 → 204")
    void 收得下浏览器的报告() throws Exception {
        assertEquals(204, send("application/csp-report", "same-origin").statusCode(),
                "这个接口收不下的话，Report-Only 期间的违规一条都看不到，也就永远没法切成强制执行");
    }

    @Test
    @DisplayName("新版 Reporting API 的 application/reports+json 也收")
    void 新格式也收() throws Exception {
        assertEquals(204, send("application/reports+json", "same-origin").statusCode());
    }

    @Test
    @DisplayName("别的站点发来的报告被跨站防护拦下 —— 它不该成为谁都能灌的日志口")
    void 跨站报告被拒() throws Exception {
        assertEquals(403, send("application/csp-report", "cross-site").statusCode());
    }

    @Test
    @DisplayName("刷爆限流也只是不记日志，接口照常 204 —— 不让浏览器反复重试")
    void 超限照常返回() throws Exception {
        for (int i = 0; i < 40; i++) {
            assertEquals(204, send("application/csp-report", null).statusCode());
        }
    }
}
