package solvela.app.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import solvela.member.api.AuthFailReason;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberIdentity;
import solvela.member.api.MemberLogoutCmd;
import solvela.member.api.MemberPasswordPolicy;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.RegisterFailReason;
import solvela.enums.GenderEnum;

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
 *
 * <h3>🔴 下游被桩掉了，这是刻意的</h3>
 * 拆成四个进程之后，登录要经 HTTP 调会员服务。如果这里用真下游：
 * <ul>
 *   <li>会员服务没起时，整套契约测试全红 —— 而它们要验的<b>根本不是会员服务</b>；</li>
 *   <li>断言会变成「会员服务的行为对不对」，而网关自己的翻译规则
 *       （{@code AuthFailReason} → HTTP 状态码）反倒没人守。</li>
 * </ul>
 * 桩掉之后，本类专注一件事：<b>给定域返回的 reason，网关吐出什么状态码和 code</b>。
 * 域本身的行为由会员服务自己的测试负责。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiContractTest.StubDownstream.class)
class ApiContractTest {

    /**
     * 桩掉会员服务。只按<b>输入形状</b>给结果，不做任何真实逻辑 ——
     * 它存在的意义是让网关那张翻译表跑起来，不是模拟一个会员域。
     */
    @TestConfiguration
    static class StubDownstream {

        @Bean
        @Primary
        MemberAuthApi stubMemberAuthApi() {
            return new MemberAuthApi() {
                @Override
                public MemberAuthResult authenticate(MemberAuthCmd cmd) {
                    // 只区分「像不像手机号」这一件事：契约测试要的是
                    // BAD_PHONE_FORMAT → 400、BAD_CREDENTIALS → 401 这两条映射成立
                    if (cmd.phone() == null || !cmd.phone().matches("[0-9]{11}")) {
                        return MemberAuthResult.fail(AuthFailReason.BAD_PHONE_FORMAT);
                    }
                    return MemberAuthResult.fail(AuthFailReason.BAD_CREDENTIALS);
                }

                /**
                 * 桩：按手机号的最后一位分派到各个 reason，让网关那张注册翻译表
                 * 每条分支都能被真实 HTTP 请求走一遍。
                 * 域自己的规则（格式、强度、限频）由会员域的测试负责，这里不重复。
                 */
                @Override
                public MemberRegisterResult register(MemberRegisterCmd cmd) {
                    if (cmd.phone() == null || !cmd.phone().matches("[0-9]{11}")) {
                        return MemberRegisterResult.fail(RegisterFailReason.BAD_PHONE_FORMAT);
                    }
                    if (cmd.phone().endsWith("1")) {
                        return MemberRegisterResult.fail(RegisterFailReason.PHONE_TAKEN);
                    }
                    if (cmd.phone().endsWith("2")) {
                        return MemberRegisterResult.fail(RegisterFailReason.WEAK_PASSWORD);
                    }
                    if (cmd.phone().endsWith("3")) {
                        return MemberRegisterResult.tooManyAttempts(90L);
                    }
                    return MemberRegisterResult.ok(new MemberIdentity(
                            1000000001L, "sv1000000001", "会员1000000001", null, GenderEnum.UNKNOWN));
                }

                @Override
                public MemberIdentity getAuthIdentity(Long memberId) {
                    return null;
                }

                @Override
                public void recordLogout(MemberLogoutCmd cmd) {
                }
            };
        }
    }

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
    @DisplayName("注册成功 → 200，直接带回令牌（不用再登一次）")
    void 注册成功直接返回令牌() {
        HttpResponse<String> response =
                post("/auth/register", "{\"phone\":\"13800000000\",\"password\":\"abcd1234\"}", null);

        assertEquals(200, response.statusCode(), "实际响应：" + response.body());

        JsonNode body = parse(response);
        assertFalse(body.path("accessToken").asText().isBlank(),
                "注册完必须直接给令牌 —— 让用户注册完再登一次是纯多余的一步，"
                        + "而多一次调用就多一次失败的机会");
        assertTrue(body.path("expiresIn").asLong() > 0, "要给有效期，客户端据此提前续期");
        assertEquals("sv1000000001", body.path("member").path("memberName").asText(),
                "账号由会员域自动生成，不需要用户在注册时起");
    }

    @Test
    @DisplayName("🔴 注册接口不能泄露密码或手机号")
    void 注册响应里没有敏感信息() {
        HttpResponse<String> response =
                post("/auth/register", "{\"phone\":\"13800000000\",\"password\":\"abcd1234\"}", null);

        String raw = response.body();
        assertFalse(raw.contains("13800000000"),
                "响应里出现了手机号。MemberIdentity 刻意不带它 —— 这个对象会进 Redis、进日志，"
                        + "放明文手机号等于让整套 PII 加密失效。实际响应：" + raw);
        assertFalse(raw.contains("abcd1234"), "响应里出现了明文密码。实际响应：" + raw);
        assertFalse(raw.contains("$argon2"), "响应里出现了密码哈希。实际响应：" + raw);
    }

    @Test
    @DisplayName("手机号已注册 → 409，且明说，不含糊")
    void 手机号已注册返回409() {
        HttpResponse<String> response =
                post("/auth/register", "{\"phone\":\"13800000001\",\"password\":\"abcd1234\"}", null);

        assertEquals(409, response.statusCode(),
                "用 409 而不是 400：这不是「你填错了」，是「服务端已有一个冲突的东西」，"
                        + "前端据此可以直接引导去登录页。实际响应：" + response.body());

        JsonNode body = parse(response);
        assertEquals("CONFLICT", body.path("code").asText());
        assertTrue(body.path("message").asText().contains("已注册"),
                "注册的措辞与登录【正好相反】：登录要含糊（防手机号枚举），"
                        + "注册必须明说，否则用户不知道该去登录还是换号。实际：" + body.path("message").asText());
    }

    @Test
    @DisplayName("密码太弱 → 400，且把规则原文给出来")
    void 密码太弱返回400() {
        HttpResponse<String> response =
                post("/auth/register", "{\"phone\":\"13800000002\",\"password\":\"abcd1234\"}", null);

        assertEquals(400, response.statusCode(), "实际响应：" + response.body());

        JsonNode body = parse(response);
        assertEquals("INVALID_ARGUMENT", body.path("code").asText());
        assertEquals(MemberPasswordPolicy.HINT, body.path("message").asText(),
                "提示文案必须来自 MemberPasswordPolicy.HINT —— 在网关再写一遍，"
                        + "改规则时两处措辞就会对不上");
    }

    @Test
    @DisplayName("注册过于频繁 → 429，并告诉还要等多久")
    void 注册限频返回429() {
        HttpResponse<String> response =
                post("/auth/register", "{\"phone\":\"13800000003\",\"password\":\"abcd1234\"}", null);

        assertEquals(429, response.statusCode(), "实际响应：" + response.body());

        JsonNode body = parse(response);
        assertEquals("OPERATION_LIMITED", body.path("code").asText());
        // 域给的是 90 秒，向上取整到 2 分钟 —— 剩 90 秒时说「请 1 分钟后重试」用户会白点一次
        assertTrue(body.path("message").asText().contains("2 分钟"),
                "秒数要向上取整成人话。实际：" + body.path("message").asText());
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
