package solvela.member.server;

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
 * 会员服务的两件事，只有真起进程才证明得了：装配清单能不能立起来、
 * 接口上的 {@code @HttpExchange} 有没有被映射成真实端点。
 *
 * <p>营销服务那边的同名测试已经替这套做法交过一次学费：它抓出了漏配的 MapperScan，
 * 还抓出了两个契约撞同一个 URL（启动期 Ambiguous mapping）。
 * 这类问题<b>编译期完全看不出来</b> —— controller 照样实现了接口，代码照样能编过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InternalEndpointMappingTest {

    /** 真实业务不会出现的会员号 —— 本测试不造数，只走「查不到」那条路 */
    private static final long NOT_EXIST_MEMBER = 9_999_999_997L;

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("取身份的端点已映射（不是 404），查不到会员时返回空")
    void 身份端点已映射() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/internal/member/auth/identity/" + NOT_EXIST_MEMBER))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "期望端点存在。404 意味着 Spring MVC 没认接口上的 @HttpExchange，"
                        + "「契约只定义一次」这个做法就不成立了。实际 body: " + response.body());
        assertTrue(response.body().isBlank(),
                "查不到会员时契约返回 null，body 应为空。实际: " + response.body());
    }

    @Test
    @DisplayName("认证端点已映射，且请求真的走到了域里")
    void 认证端点已映射且转发到位() throws Exception {
        String body = """
                {"phone":"13800000000","password":"__mapping_test__","deviceType":"H5","clientIp":"127.0.0.1"}
                """;

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/member/auth/verify"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "实际 body: " + response.body());
        // 这个手机号在库里不存在 → 域里按摘要查不到人 → BAD_CREDENTIALS。
        // 拿到它证明：路由到了薄壳 → 转发给了 MemberAuthService → 域真的查了库。
        // 顺带钉住一条安全契约：查无此人与密码错误必须给<b>同一个</b> reason，
        // 分开说等于免费送出一个「这个号注册过没有」的查询接口
        assertTrue(response.body().contains("BAD_CREDENTIALS"),
                "期望域里的认证逻辑生效并返回 BAD_CREDENTIALS；实际 body: " + response.body());
    }
}
