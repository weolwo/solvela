package solvela.app.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端钉住 C 端的返回契约。
 *
 * <p>这套契约的核心是<b>真实 HTTP 状态码</b>：上一版所有响应都是 200，
 * 业务失败靠 body 里的 code 区分。后果是具体的 —— 网关按状态码做的熔断和限流全部失效，
 * APM 面板上成功率永远 100%，客户端没法在 HTTP 拦截器里统一处理 401，
 * 重试策略也没法写（一个 200 到底该不该重试？）。
 *
 * <p>状态码这种东西，单元测试测不出来：controller 返回什么对象、
 * 异常处理器怎么映射、Spring 最后实际写出什么状态，中间隔着好几层。
 * 所以这里真起服务、真发 HTTP 请求、真读状态行。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiContractTest {

    @LocalServerPort
    private int port;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("未登录调需要登录的接口 → 401，不是 200")
    void 未登录返回401() {
        HttpResponse<String> response = post("/auth/me", "{}", null);

        assertEquals(401, response.statusCode(),
                "未登录必须是 401。返回 200 的话，客户端没法在 HTTP 拦截器里统一跳登录页，"
                        + "网关也看不出这个请求失败了。实际响应：" + response.body());

        JsonNode body = parse(response);
        assertEquals("LOGIN_REQUIRED", body.path("code").asText(),
                "错误码要稳定且自解释，客户端按它分支");
        assertFalse(body.path("message").asText().isBlank(), "要有一句能直接展示给用户的话");
    }

    @Test
    @DisplayName("参数校验失败 → 400，且带上具体哪个字段不对")
    void 参数错误返回400() {
        HttpResponse<String> response = post("/auth/login", "{\"phone\":\"\",\"password\":\"\"}", null);

        assertEquals(400, response.statusCode(), "实际响应：" + response.body());

        JsonNode body = parse(response);
        assertEquals("INVALID_ARGUMENT", body.path("code").asText());
        // 校验注解上的 message 要原样带给用户 —— 「请输入手机号」能让人自己改对，
        // 「参数有误」只会换来一条工单
        assertTrue(body.path("message").asText().contains("手机号")
                        || body.path("message").asText().contains("密码"),
                "应带上具体是哪个字段的问题，实际：" + body.path("message").asText());
    }

    @Test
    @DisplayName("手机号格式不对 → 400 并明说；这不泄露任何账号是否存在")
    void 手机号格式错误() {
        HttpResponse<String> response =
                post("/auth/login", "{\"phone\":\"not-a-phone\",\"password\":\"whatever\"}", null);

        assertEquals(400, response.statusCode(), "实际响应：" + response.body());
        assertEquals("INVALID_ARGUMENT", parse(response).path("code").asText());
    }

    @Test
    @DisplayName("伪造的令牌 → 401，且不能因此崩")
    void 伪造令牌() {
        HttpResponse<String> response = post("/auth/me", "{}", "mb_" + "A".repeat(43));

        assertEquals(401, response.statusCode(), "实际响应：" + response.body());
        assertEquals("LOGIN_REQUIRED", parse(response).path("code").asText());
    }

    @Test
    @DisplayName("管理端的 sa-token 令牌在 C 端一文不值")
    void 管理端令牌无效() {
        // 上一版两端共用默认 loginType，员工 token 能通过会员端的登录校验，
        // 只剩「loginId 前缀是不是 2:」一道字符串判断在挡着。
        // 现在两套令牌连格式都不一样，压根不在一个存储命名空间里。
        HttpResponse<String> response =
                post("/auth/me", "{}", "a1b2c3d4-e5f6-7890-abcd-ef1234567890");

        assertEquals(401, response.statusCode(), "实际响应：" + response.body());
    }

    @Test
    @DisplayName("不存在的路径 → 404，不是 401")
    void 路径不存在返回404() {
        HttpResponse<String> response = post("/no/such/endpoint", "{}", null);

        assertEquals(404, response.statusCode(),
                "拦截器如果把 /error 也拦了，这里会变成 401 —— "
                        + "看起来像「这个接口需要登录」，排查时会往完全错误的方向找。实际：" + response.body());
    }

    @Test
    @DisplayName("每个响应都带 traceId，用户报障时凭它定位")
    void 响应带链路id() {
        HttpResponse<String> response = post("/auth/me", "{}", null);

        String header = response.headers().firstValue("traceId").orElse(null);
        assertNotNull(header, "响应头里没有 traceId");
        assertEquals(header, parse(response).path("traceId").asText(),
                "响应头和响应体里的 traceId 应当是同一个");
    }

    @Test
    @DisplayName("客户端塞进来的畸形 traceId 不会原样透传")
    void 拒绝伪造的链路id() {
        // 裸的换行符进不了 HTTP 头（JDK 的 HttpClient 在构造请求时就拒绝），
        // 所以真实的日志注入走的是编码变体。这里用一个 HTTP 层合法、
        // 但含有 traceId 不该出现的字符的值 —— 它必须被整体丢弃并重新生成。
        String malicious = "abc%0d%0a[ERROR]:injected";
        HttpResponse<String> response = HttpClientHolder.send(
                HttpRequest.newBuilder(URI.create(base() + "/auth/me"))
                        .header("Content-Type", "application/json")
                        .header("traceId", malicious)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build());

        String actual = response.headers().firstValue("traceId").orElse("");
        assertNotEquals(malicious, actual, "畸形 traceId 被原样接受了");
        assertTrue(actual.matches("[A-Za-z0-9-]{1,32}"),
                "重新生成的 traceId 应当只含字母数字与连字符，实际：" + actual);
    }

    @Test
    @DisplayName("规矩的 traceId 会被沿用 —— 校验不是一刀切拒绝")
    void 接受合法的链路id() {
        String given = "client-abc-123";
        HttpResponse<String> response = HttpClientHolder.send(
                HttpRequest.newBuilder(URI.create(base() + "/auth/me"))
                        .header("Content-Type", "application/json")
                        .header("traceId", given)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build());

        assertEquals(given, response.headers().firstValue("traceId").orElse(null),
                "合法的 traceId 应当沿用 —— 客户端埋点要靠它和服务端日志串起来");
    }

    // ------------------------------------------------------------------ 工具

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> post(String path, String body, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClientHolder.send(builder.build());
    }

    private JsonNode parse(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body());
        } catch (Exception e) {
            throw new AssertionError("响应不是合法 JSON：" + response.body(), e);
        }
    }

    /** 复用一个 HttpClient，省得每个请求建一次连接池。 */
    private static final class HttpClientHolder {
        private static final HttpClient CLIENT = HttpClient.newHttpClient();

        static HttpResponse<String> send(HttpRequest request) {
            try {
                return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new IllegalStateException("请求失败：" + request.uri(), e);
            }
        }
    }
}
