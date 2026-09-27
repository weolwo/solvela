package solvela.app.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import solvela.app.auth.MemberPrincipalLoader;
import solvela.app.auth.WebCookieProperties;
import solvela.apptest.stub.CookieSessionStub;
import solvela.auth.member.MemberTokenStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Web 端 HttpOnly cookie 会话：真起端口、真发 HTTP、令牌真进 Redis。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>🔴 cookie 模式下<b>响应体里没有令牌</b> —— 有的话 HttpOnly 白做；</li>
 *   <li>🔴 {@code /auth/session/adopt} <b>只能单向</b>（请求头 → cookie），不存在 cookie → 响应体的出口；</li>
 *   <li>🔴 凭 cookie 调「下线其他设备」<b>不会把自己踢掉</b>（改造前「当前令牌」只从请求头取）；</li>
 *   <li>🔴 浏览器发起的跨源写请求被拒，包括登录（登录 CSRF）；App / Postman 不受影响；</li>
 *   <li>设备注册在已有身份时复用，不新建。</li>
 * </ul>
 *
 * @Date 2026-09-26
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CookieSessionStub.class)
@TestPropertySource(properties = {
        // 本类测的不是滑块：发码、密码登录的滑块由 CaptchaFlowTest 负责
        "solvela.app.captcha.send-code=false",
        "solvela.app.captcha.password-login=false",
})
class CookieSessionTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String REGISTER_BODY = "{\"identity\":\"sv@example.com\",\"registerType\":\"EMAIL_CODE\"%s}";

    @LocalServerPort
    private int port;

    @Autowired
    private WebCookieProperties cookieProperties;

    @Autowired
    private MemberTokenStore tokenStore;

    @Autowired
    private MemberPrincipalLoader principalLoader;

    @Autowired
    private CookieSessionStub stub;

    @AfterEach
    void cleanUp() {
        tokenStore.revokeAll(CookieSessionStub.MEMBER_ID);
        principalLoader.evict(CookieSessionStub.MEMBER_ID);
    }

    // ============================== 下发 ==============================

    @Test
    @DisplayName("🔴 cookie 模式注册：令牌只在 Set-Cookie 里，响应体里没有")
    void cookie模式响应体无令牌() throws Exception {
        HttpResponse<String> response = post("/auth/register", String.format(REGISTER_BODY, ",\"useCookie\":true"), Map.of());

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertTrue(body.path("accessToken").isNull() || body.path("accessToken").isMissingNode(),
                "响应体里有令牌的话，混进页面的脚本包一层 fetch 就能截走它，HttpOnly 等于白做。实际：" + response.body());

        String setCookie = setCookie(response, cookieProperties.sessionCookieName()).orElseThrow(
                () -> new AssertionError("cookie 模式下必须下发会话 cookie"));
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        assertTrue(setCookie.contains("Path=/"), setCookie);
        assertFalse(setCookie.toLowerCase().contains("domain="),
                "不能带 Domain：带了就会发给所有子域，__Host- 前缀也会被浏览器拒收。实际：" + setCookie);
        assertTrue(setCookie.contains("Max-Age="), "注册固定持久 cookie（没有「记住我」）。实际：" + setCookie);
    }

    @Test
    @DisplayName("不传 useCookie（App / Postman）：令牌照旧在响应体里，也不下发 cookie")
    void 请求头模式照旧() throws Exception {
        HttpResponse<String> response = post("/auth/register", String.format(REGISTER_BODY, ""), Map.of());

        assertEquals(200, response.statusCode(), response.body());
        assertFalse(JSON.readTree(response.body()).path("accessToken").asText().isBlank());
        assertTrue(setCookie(response, cookieProperties.sessionCookieName()).isEmpty());
    }

    @Test
    @DisplayName("登录不勾「记住我」→ 会话 cookie（没有 Max-Age，关浏览器即失效）")
    void 不记住我是会话cookie() throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"loginType\":\"EMAIL_CODE\",\"identity\":\"sv@example.com\",\"credential\":\"123456\","
                        + "\"useCookie\":true,\"remember\":false}", Map.of());

        assertEquals(200, response.statusCode(), response.body());
        String setCookie = setCookie(response, cookieProperties.sessionCookieName()).orElseThrow();
        assertFalse(setCookie.contains("Max-Age="), "共用设备上不勾记住我，关掉浏览器就不该还登着。实际：" + setCookie);
    }

    // ============================== 认证 ==============================

    @Test
    @DisplayName("凭 cookie 就能认出人；退出后 cookie 被清掉、令牌被吊销")
    void cookie认证与退出() throws Exception {
        String cookie = registerByCookie();

        assertEquals(200, post("/auth/me", "{}", Map.of("Cookie", cookie)).statusCode());

        HttpResponse<String> logout = post("/auth/logout", "{}", Map.of("Cookie", cookie));
        assertEquals(204, logout.statusCode(), logout.body());
        String cleared = setCookie(logout, cookieProperties.sessionCookieName()).orElseThrow(
                () -> new AssertionError("退出要清 cookie —— 页面脚本删不掉 HttpOnly cookie，只有服务端能做"));
        assertTrue(cleared.contains("Max-Age=0"), cleared);

        assertEquals(401, post("/auth/me", "{}", Map.of("Cookie", cookie)).statusCode(),
                "只清 cookie 不吊销的话，之前被复制走的那份令牌照样能用");
    }

    @Test
    @DisplayName("🔴 凭 cookie「下线其他设备」：别的会话下线，自己不掉")
    void 下线其他不踢自己() throws Exception {
        String mine = registerByCookie();
        String otherToken = JSON.readTree(post("/auth/register", String.format(REGISTER_BODY, ""), Map.of()).body())
                .path("accessToken").asText();

        assertEquals(204, post("/auth/sessions/revokeOthers", "{}", Map.of("Cookie", mine)).statusCode());

        assertEquals(200, post("/auth/me", "{}", Map.of("Cookie", mine)).statusCode(),
                "改造前「当前令牌」只从 Authorization 头取，cookie 模式下取到 null —— 于是把自己也踢了");
        assertEquals(401, post("/auth/me", "{}", Map.of("Authorization", "Bearer " + otherToken)).statusCode());
    }

    @Test
    @DisplayName("「我的登录设备」凭 cookie 也能标出本机")
    void 会话列表标出本机() throws Exception {
        String mine = registerByCookie();

        JsonNode sessions = JSON.readTree(post("/auth/sessions", "{}", Map.of("Cookie", mine)).body());

        assertTrue(sessions.isArray() && !sessions.isEmpty(), sessions.toString());
        assertTrue(sessions.get(0).path("current").asBoolean(),
                "标不出本机的话，用户不敢点任何一个下线按钮 —— 怕把自己踢掉。实际：" + sessions);
    }

    // ============================== 迁移 ==============================

    @Test
    @DisplayName("adopt：请求头里的旧令牌原样搬进 cookie，响应体为空")
    void adopt请求头到cookie() throws Exception {
        String token = JSON.readTree(post("/auth/register", String.format(REGISTER_BODY, ""), Map.of()).body())
                .path("accessToken").asText();

        HttpResponse<String> response = post("/auth/session/adopt", "{\"remember\":true}",
                Map.of("Authorization", "Bearer " + token));

        assertEquals(204, response.statusCode(), response.body());
        assertTrue(response.body().isEmpty());
        String setCookie = setCookie(response, cookieProperties.sessionCookieName()).orElseThrow();
        assertTrue(setCookie.startsWith(cookieProperties.sessionCookieName() + "=" + token + ";"),
                "搬的必须是【同一个】令牌：重新签发的话「我的登录设备」会凭空多一条。实际：" + setCookie);
    }

    @Test
    @DisplayName("🔴 adopt 不存在反方向：凭 cookie 调它拿不到任何令牌")
    void adopt不能反向() throws Exception {
        String cookie = registerByCookie();

        HttpResponse<String> response = post("/auth/session/adopt", "{}", Map.of("Cookie", cookie));

        assertEquals(204, response.statusCode());
        assertTrue(response.body().isEmpty(),
                "任何「cookie → 响应体」的出口都会让 XSS 把 HttpOnly 令牌取出来。实际：" + response.body());
        assertTrue(setCookie(response, cookieProperties.sessionCookieName()).isEmpty());
    }

    // ============================== 跨站防护 ==============================

    @Test
    @DisplayName("🔴 浏览器发起的跨站 / 同站（兄弟子域）写请求 → 403；同源 → 放行")
    void 跨站写请求被拒() throws Exception {
        String cookie = registerByCookie();

        assertEquals(403, post("/auth/me", "{}", Map.of("Cookie", cookie, "Sec-Fetch-Site", "cross-site")).statusCode());
        assertEquals(403, post("/auth/me", "{}", Map.of("Cookie", cookie, "Sec-Fetch-Site", "same-site")).statusCode(),
                "admin. 与 app. 是同一个 site，SameSite 挡不住它 —— 这一条正是要靠这里挡");
        assertEquals(200, post("/auth/me", "{}", Map.of("Cookie", cookie, "Sec-Fetch-Site", "same-origin")).statusCode());
    }

    @Test
    @DisplayName("🔴 登录 CSRF：别的网站替浏览器提交登录 → 403，不会落下会话 cookie")
    void 登录CSRF被拒() throws Exception {
        HttpResponse<String> response = post("/auth/register", String.format(REGISTER_BODY, ",\"useCookie\":true"),
                Map.of("Sec-Fetch-Site", "cross-site"));

        assertEquals(403, response.statusCode());
        assertTrue(setCookie(response, cookieProperties.sessionCookieName()).isEmpty(),
                "落下 cookie 的话，受害者此后绑的地址、充的钱都进了攻击者的号");
    }

    @Test
    @DisplayName("老浏览器（无 Sec-Fetch-Site）：看 Origin —— 自己的源放行，别的源拒绝")
    void 老浏览器看Origin() throws Exception {
        String cookie = registerByCookie();
        String self = "http://localhost:" + port;

        assertEquals(200, post("/auth/me", "{}", Map.of("Cookie", cookie, "Origin", self)).statusCode());
        assertEquals(403, post("/auth/me", "{}", Map.of("Cookie", cookie, "Origin", "https://evil.example")).statusCode());
    }

    @Test
    @DisplayName("GET 不受跨站防护影响（GET 本来就不该有副作用）")
    void GET不拦() throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(base() + "/activity"))
                .header("Sec-Fetch-Site", "cross-site").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertNotEquals(403, response.statusCode(), response.body());
    }

    // ============================== 设备 ==============================

    @Test
    @DisplayName("设备注册（cookie 模式）：令牌只进 cookie；带着 cookie 再调 → 复用同一台，不新建")
    void 设备注册复用() throws Exception {
        int before = stub.deviceRegisterCalls.get();
        HttpResponse<String> first = post("/device/register", "{\"deviceType\":\"H5\",\"useCookie\":true}", Map.of());
        assertEquals(200, first.statusCode(), first.body());
        JsonNode firstBody = JSON.readTree(first.body());
        assertTrue(firstBody.path("deviceToken").isNull() || firstBody.path("deviceToken").isMissingNode(),
                "设备令牌同样不进响应体。实际：" + first.body());
        String deviceCookie = cookiePair(setCookie(first, cookieProperties.deviceCookieName()).orElseThrow());

        HttpResponse<String> second = post("/device/register", "{\"deviceType\":\"H5\",\"useCookie\":true}",
                Map.of("Cookie", deviceCookie));

        assertEquals(firstBody.path("deviceId").asText(), JSON.readTree(second.body()).path("deviceId").asText(),
                "同一台设备每次启动都来调，每次都新建的话一个人会凭空多出一串设备");
        assertEquals(before + 1, stub.deviceRegisterCalls.get(), "第二次不该再走到会员域的新建");
        assertTrue(setCookie(second, cookieProperties.deviceCookieName()).isPresent(), "复用时也要续期 cookie");
    }

    // ============================== 工具 ==============================

    /** cookie 模式注册一次，返回可以直接塞进 Cookie 头的 name=value */
    private String registerByCookie() throws Exception {
        HttpResponse<String> response = post("/auth/register", String.format(REGISTER_BODY, ",\"useCookie\":true"), Map.of());
        assertEquals(200, response.statusCode(), response.body());
        return cookiePair(setCookie(response, cookieProperties.sessionCookieName()).orElseThrow());
    }

    private static String cookiePair(String setCookie) {
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private static Optional<String> setCookie(HttpResponse<?> response, String name) {
        List<String> all = response.headers().allValues("Set-Cookie");
        return all.stream().filter(v -> v.startsWith(name + "=")).findFirst();
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertNotNull(response);
        return response;
    }
}
