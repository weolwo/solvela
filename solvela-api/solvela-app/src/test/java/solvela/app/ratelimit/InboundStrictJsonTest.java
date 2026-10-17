package solvela.app.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import solvela.app.config.InboundJsonConfig;
import solvela.app.domain.MemberLoginRequest;
import solvela.apptest.stub.ApiContractDownstreamStub;
import solvela.security.SecurityIncidentEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 请求体按严格模式解析（网关方案 §5.6，{@link InboundJsonConfig}）—— 真起服务、真发 HTTP，测的是<b>接线</b>：
 * 严格的 mapper 真的接在了 Controller 上，而解析下游响应的那个 mapper 没有被一起改严。
 *
 * <p>解析上限本身（嵌套、数字长度、字段名长度）在 {@code InboundJsonConfigTest} 里按 mapper 测，这里不重复。
 *
 * @Date 2026-10-17
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ApiContractDownstreamStub.class, InboundStrictJsonTest.TestQueue.class})
@TestPropertySource(properties = {
        "solvela.app.captcha.send-code=false",
        "solvela.app.captcha.password-login=false",
        // 同一个 127.0.0.1 连着注册十几次必然被限流，那不是本类要测的
        "solvela.app.rate-limit.enabled=false",
})
class InboundStrictJsonTest {

    /** 事件投进这个类自己的队列，免得本机的落库任务把用例造的事件写进库 */
    static final String QUEUE = "test:inbound-json:" + UUID.randomUUID();

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TestConfiguration
    static class TestQueue {
        @Bean
        @Primary
        IncidentPublisher testIncidentPublisher(StringRedisTemplate redis) {
            return new IncidentPublisher(redis, QUEUE);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redis;

    /** Spring 容器里那个 mapper：网关拿它解析 app-biz 的响应 */
    @Autowired
    private JsonMapper appMapper;

    @AfterEach
    void cleanUp() {
        redis.delete(QUEUE);
    }

    @Test
    @DisplayName("🔴 前端拼错字段名 → 400「未知字段 phoneNum」，记一条只记录、不处罚的事件")
    void unknownFieldIsNamed() {
        HttpResponse<String> response = post("/auth/register",
                "{\"identity\":\"13800000000\",\"password\":\"abcd1234\",\"phoneNum\":\"13800000000\"}");

        assertEquals(400, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertEquals("INVALID_ARGUMENT", body.path("code").asString());
        assertEquals("未知字段 phoneNum", body.path("message").asString(),
                "要说清是哪个字段 —— 一句「请求参数有误」只会换来一次排查");

        List<String> queued = redis.opsForList().range(QUEUE, 0, -1);
        assertNotNull(queued);
        assertEquals(1, queued.size(), "撞上未知字段要记一条事件");
        SecurityIncidentEvent event = JSON.readValue(queued.getFirst(), SecurityIncidentEvent.class);
        assertEquals(SecurityIncidentEvent.UNKNOWN_FIELD, event.incidentType());
        assertEquals(SecurityIncidentEvent.ACTION_RECORD, event.action(),
                "低置信：前后端版本错开时真人也会撞到，只记录、不拉名单");
        assertEquals(null, event.penaltyUntilEpochSec(), "不处罚");
        assertEquals("phoneNum", event.matchedParam());
        assertEquals(SecurityIncidentEvent.FIELD_BODY, event.matchedField());
        assertEquals("/auth/register", event.requestPath());
    }

    @Test
    @DisplayName("同一个 IP 带同一个未知字段：每次都 400，事件十分钟只记一次")
    void sameUnknownFieldIsRecordedOnce() {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> response = post("/auth/login",
                    "{\"identity\":\"13800000000\",\"credential\":\"abcd1234\",\"rememberMe\":true}");
            assertEquals(400, response.statusCode(), response.body());
        }
        assertEquals(1L, redis.opsForList().size(QUEUE),
                "发错一个前端版本时每个人每次点击都会撞上 —— 每次都记的话，事件表里全是同一句话");
    }

    @Test
    @DisplayName("🔴 重复的键 → 400：不同的解析器可能读出两个答案，安检门扫的与 Controller 绑的未必是同一个")
    void duplicateKeyIsRejected() {
        HttpResponse<String> response = post("/auth/register",
                "{\"identity\":\"13800000000\",\"identity\":\"13800000009\",\"password\":\"abcd1234\"}");

        assertEquals(400, response.statusCode(), response.body());
        assertEquals("INVALID_ARGUMENT", JSON.readTree(response.body()).path("code").asString());
    }

    @Test
    @DisplayName("🔴 请求体超过 64KB → 400，在解析阶段就停下，不是 500")
    void oversizedDocumentIsRejected() {
        String padding = " ".repeat(InboundJsonConfig.MAX_DOCUMENT_BYTES + 1);
        HttpResponse<String> response = post("/auth/register",
                "{\"identity\":\"13800000000\"," + padding + "\"password\":\"abcd1234\"}");

        assertEquals(400, response.statusCode(), response.body());
        assertEquals("请求参数有误", JSON.readTree(response.body()).path("message").asString());
    }

    @Test
    @DisplayName("超长字符串：解析上限以内由字段的 @Size 说人话；超过解析上限的在解析阶段就停下")
    void longStrings() {
        HttpResponse<String> sized = post("/auth/login",
                "{\"identity\":\"" + "1".repeat(300) + "\",\"credential\":\"abcd1234\"}");
        assertEquals(400, sized.statusCode(), sized.body());
        assertEquals("手机号或邮箱过长", JSON.readTree(sized.body()).path("message").asString());

        HttpResponse<String> huge = post("/auth/login",
                "{\"identity\":\"" + "1".repeat(InboundJsonConfig.MAX_STRING_LENGTH + 1) + "\",\"credential\":\"abcd1234\"}");
        assertEquals(400, huge.statusCode(), huge.body());
        assertEquals("请求参数有误", JSON.readTree(huge.body()).path("message").asString());
    }

    @Test
    @DisplayName("🔴 解析下游响应的 mapper 保持宽松：biz 先发版多了一个字段，网关不能整个接口报错")
    void downstreamMapperStaysLenient() {
        MemberLoginRequest parsed = appMapper.readValue(
                "{\"identity\":\"13800000000\",\"credential\":\"x\",\"addedByNewerVersion\":1}", MemberLoginRequest.class);

        assertEquals("13800000000", parsed.identity());
    }

    private HttpResponse<String> post(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("请求失败：" + path, e);
        }
    }
}
