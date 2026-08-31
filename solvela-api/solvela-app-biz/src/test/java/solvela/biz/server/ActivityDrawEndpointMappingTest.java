package solvela.biz.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 营销服务的两件事，只有真起进程才证明得了：
 *
 * <ol>
 *   <li><b>这份扫描清单能不能装配起来</b> —— 少列一个包就是启动失败，
 *       而清单是手写的，没有测试盯着就只能等部署时才知道；</li>
 *   <li><b>Spring MVC 认不认接口上的 {@code @HttpExchange}</b> —— 整个「契约只定义一次、
 *       两端共用」的做法都建立在这一条上。它不成立的话端点全是 404，
 *       而这在编译期<b>完全看不出来</b>：controller 照样实现了接口，代码照样能编过。</li>
 * </ol>
 *
 * <p>起真端口发真 HTTP，与 {@code ApiContractTest} 同一套做法：
 * 要验的恰恰是「HTTP 这一层对不对」，用 MockMvc 会把序列化和状态码这两处一起绕过去。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivityDrawEndpointMappingTest {

    /** 真实业务不会出现的编码 —— 本测试不造数，只走「查不到」那条路 */
    private static final String NOT_EXIST = "__MAPPING_TEST_NO_SUCH_ACTIVITY__";

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("接口上的 @GetExchange 被映射成了真实端点（不是 404）")
    void 活动详情端点已映射() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/internal/activity/" + NOT_EXIST + "/rule"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // 活动不存在时契约返回 null → 200 + 空 body。
        // 拿到 404 说明映射根本没生效 —— 那才是这条断言要防的
        assertEquals(200, response.statusCode(),
                "期望端点存在。404 意味着 Spring MVC 没有认接口上的 @HttpExchange，"
                        + "「契约只定义一次」这个做法就不成立了。实际 body: " + response.body());
    }

    @Test
    @DisplayName("接口上的 @PostExchange 被映射，且请求真的走到了域里")
    void 抽奖端点已映射且转发到位() throws Exception {
        String body = """
                {"activityCode":"%s","memberId":9999999999,"requestId":"mapping-test","params":{}}
                """.formatted(NOT_EXIST);

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/internal/activity/draw"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "实际 body: " + response.body());
        // 拿到这个 reason 证明：路由到了薄壳 → 转发给了 ActivityFacade → 域做了活动校验。
        // 中间断在任何一处，body 里都不会是 ACTIVITY_NOT_FOUND
        assertTrue(response.body().contains("ACTIVITY_NOT_FOUND"),
                "期望域里的活动校验生效并返回 reject；实际 body: " + response.body());
        assertTrue(response.body().contains("\"hit\":false"), "实际 body: " + response.body());
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
