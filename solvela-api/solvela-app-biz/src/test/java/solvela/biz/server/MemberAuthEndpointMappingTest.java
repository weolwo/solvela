package solvela.biz.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;

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
class MemberAuthEndpointMappingTest {

    /** 真实业务不会出现的会员号 —— 本测试不造数，只走「查不到」那条路 */
    private static final long NOT_EXIST_MEMBER = 9_999_999_997L;

    /**
     * 每次换一个不存在的手机号。
     *
     * <h3>🔴 不能写死一个号</h3>
     * 密码连续错误会按<b>身份摘要</b>攒冷却计数（查无此人也照样攒 —— 那正是不让「会不会被冷却」
     * 变成账号枚举接口的做法），而那个计数在 Redis 里留 24 小时。
     * 写死一个号的话，本类跑过几遍之后这个号就进了冷却，用例从「查无此人」变成「操作过于频繁」，
     * 而它想证明的只是「请求走到了域里」。
     */
    private static String freshPhone() {
        return "139" + (10_000_000 + ThreadLocalRandom.current().nextInt(80_000_000));
    }

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
        // captchaPassed 为 true：全局滑块默认开着，不带它的话会停在「要先过滑块」，
        // 就证明不了「请求走到了域里的认证逻辑」这件事
        String body = """
                {"loginType":"PHONE_PASSWORD","identity":"%s","credential":"__mapping_test__",\
                "deviceType":"H5","clientIp":"127.0.0.1","captchaPassed":true}
                """.formatted(freshPhone());

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

    @Test
    @DisplayName("🔴 请求体不带 captchaPassed 照样进得来 —— 这个字段在线上可以缺")
    void 缺字段不该变成400() throws Exception {
        /*
         * captchaPassed 是 2026-10-15 新加的。如果把它声明成原始类型 boolean，
         * Jackson 会在反序列化阶段就把【任何不带这个字段的请求体】挡成 400
         * （「Cannot map null into type boolean」）—— 一个和业务毫无关系的错误，
         * 而老版本网关、内部工具、手写的联调请求全都不带它。
         *
         * 所以它是 Boolean，null 当作「没过滑块」。这条用例钉住的就是这件事：
         * 回什么业务结果不重要，重要的是<b>不能是 400</b>。
         */
        String body = """
                {"loginType":"PHONE_PASSWORD","identity":"%s","credential":"__mapping_test__",\
                "deviceType":"H5","clientIp":"127.0.0.1"}
                """.formatted(freshPhone());

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/member/auth/verify"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "缺一个可选字段不该变成 400。实际 body: " + response.body());
    }

    @Test
    @DisplayName("二次验证端点已映射，且请求真的走到了域里")
    void 二次验证端点已映射() throws Exception {
        // 没带设备号 → 域里第一步就判 NO_DEVICE，不查库、不碰 Redis。
        // 拿到它证明：路由到了薄壳 → 转发给了 MemberStepUpService
        String body = "{\"memberId\":" + NOT_EXIST_MEMBER + ",\"clientIp\":\"127.0.0.1\"}";

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/member/step-up/check"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "实际 body: " + response.body());
        assertTrue(response.body().contains("NO_DEVICE"), "实际 body: " + response.body());
    }
}
