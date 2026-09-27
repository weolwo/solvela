package solvela.app.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import solvela.apptest.stub.CookieSessionStub;
import solvela.auth.member.MemberTokenStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 滑块验证码端到端：真端口、真 Redis。下游用 {@link CookieSessionStub}（登录一律成功）。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>🔴 发码、密码登录不带通行票 → 403 CAPTCHA_REQUIRED；</li>
 *   <li>🔴 一张图只能验一次；一张通行票只能放行一次请求；</li>
 *   <li>邮箱验证码登录不拦（它的码在发码那一步已过过一次滑块）；</li>
 *   <li>同一 IP 出图超限 → 429（出图吃 CPU）。</li>
 * </ul>
 *
 * @Date 2026-09-27
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CookieSessionStub.class)
class CaptchaFlowTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String PASSWORD_LOGIN =
            "{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"sv@example.com\",\"credential\":\"Pass2026x\"}";

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MemberTokenStore tokenStore;

    @Test
    @DisplayName("🔴 发码、密码登录不带通行票 → 403 CAPTCHA_REQUIRED")
    void 不带票被拦() throws Exception {
        assertCode(post("/auth/email/code", "{\"scene\":\"LOGIN\",\"email\":\"a@example.com\"}", Map.of()),
                403, "CAPTCHA_REQUIRED");
        assertCode(post("/auth/sms/code", "{\"scene\":\"REGISTER\",\"phone\":\"13800000000\"}", Map.of()),
                403, "CAPTCHA_REQUIRED");
        assertCode(post("/auth/login", PASSWORD_LOGIN, Map.of()), 403, "CAPTCHA_REQUIRED");
    }

    @Test
    @DisplayName("🔴 拖对 → 拿到通行票 → 登录放行；同一张票再用 → 又被拦")
    void 拖对放行且票一次性() throws Exception {
        String token = solve(freshIp());

        assertEquals(200, post("/auth/login", PASSWORD_LOGIN, Map.of("X-Captcha-Token", token)).statusCode());
        assertCode(post("/auth/login", PASSWORD_LOGIN, Map.of("X-Captcha-Token", token)), 403, "CAPTCHA_REQUIRED");
        tokenStore.revokeAll(CookieSessionStub.MEMBER_ID);
    }

    @Test
    @DisplayName("🔴 拖错 → 400；这张图也跟着作废，拖对了也不认 —— 否则脚本能从 0 到 300 逐个试")
    void 一张图只能验一次() throws Exception {
        JsonNode challenge = create(freshIp());
        String id = challenge.path("captchaId").asText();
        int answer = answerOf(id);

        assertCode(post("/captcha/verify", verifyBody(id, answer + 60), Map.of()), 400, "CAPTCHA_FAILED");
        assertCode(post("/captcha/verify", verifyBody(id, answer), Map.of()), 400, "CAPTCHA_FAILED");
    }

    @Test
    @DisplayName("容差内算对：人手拖不到像素级")
    void 容差内算对() throws Exception {
        JsonNode challenge = create(freshIp());
        String id = challenge.path("captchaId").asText();

        assertEquals(200, post("/captcha/verify", verifyBody(id, answerOf(id) + 4), Map.of()).statusCode());
    }

    @Test
    @DisplayName("题面里没有答案")
    void 题面不带答案() throws Exception {
        JsonNode challenge = create(freshIp());

        assertFalse(challenge.has("answerX"), "答案下发给客户端，这道题就不存在了");
        assertNotNull(challenge.path("background").asText(null));
        assertNotNull(challenge.path("piece").asText(null));
    }

    @Test
    @DisplayName("邮箱验证码登录不拦 —— 它的码在发码那一步已经过过一次滑块")
    void 验证码登录不拦() throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"loginType\":\"EMAIL_CODE\",\"identity\":\"sv@example.com\",\"credential\":\"123456\"}", Map.of());

        assertEquals(200, response.statusCode(), response.body());
        tokenStore.revokeAll(CookieSessionStub.MEMBER_ID);
    }

    @Test
    @DisplayName("同一 IP 一分钟出图超过 20 张 → 429（出图吃 CPU，不限就是免费的压测口）")
    void 出图限频() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 20; i++) {
            assertEquals(200, post("/captcha", "{}", Map.of("X-Forwarded-For", ip)).statusCode());
        }
        assertEquals(429, post("/captcha", "{}", Map.of("X-Forwarded-For", ip)).statusCode());
        redis.delete("app:captcha:ip:" + ip);
    }

    // ============================== 工具 ==============================

    /** 出一张图并按 Redis 里的答案拖对，返回通行票 —— 测试里读答案，真实客户端读不到 */
    private String solve(String ip) throws Exception {
        String id = create(ip).path("captchaId").asText();
        HttpResponse<String> response = post("/captcha/verify", verifyBody(id, answerOf(id)), Map.of());
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body()).path("captchaToken").asText();
    }

    private JsonNode create(String ip) throws Exception {
        HttpResponse<String> response = post("/captcha", "{}", Map.of("X-Forwarded-For", ip));
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private int answerOf(String captchaId) {
        return Integer.parseInt(redis.opsForValue().get("app:captcha:c:" + captchaId));
    }

    private static String verifyBody(String id, int x) {
        return "{\"captchaId\":\"" + id + "\",\"x\":" + x + "}";
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, JSON.readTree(response.body()).path("code").asText());
    }

    private static String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + (1 + r.nextInt(250));
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
