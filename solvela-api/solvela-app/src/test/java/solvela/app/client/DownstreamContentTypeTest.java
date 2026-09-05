package solvela.app.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.app.config.AppJsonConfig;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.MallCommodityPageCmd;
import solvela.marketing.api.MallCommodityPageView;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关<b>发出请求</b>时的 Content-Type。与 {@link DownstreamJsonTest}（解析响应）成对。
 *
 * <h3>这个测试为什么存在（2026-09-05 的第二次踩坑）</h3>
 * {@code GET /mall/commodity} 一接通就 500，下游回的是：
 * <pre>
 *   HttpMediaTypeNotSupportedException: Content-Type 'application/yaml;charset=UTF-8' is not supported
 * </pre>
 *
 * <p>没有人配过 YAML。是 {@link DownstreamClientConfig} 换 mapper 的写法把 JSON 转换器
 * <b>从原位挪到了队尾</b>（{@code removeIf} 之后 {@code add}），而 RestClient 写请求体时
 * 取的是第一个 {@code canWrite} 的转换器 —— Spring 7 的默认顺序是
 * json → smile → cbor → <b>yaml</b> → xml，JSON 一挪到队尾，YAML 就接管了序列化。
 * 而 YAML 转换器是真被注册了：{@code com.fasterxml.jackson.dataformat.yaml.YAMLFactory}
 * 由 swagger-core-jakarta 带进 classpath。
 *
 * <p>之前一直没暴露，是因为在此之前跨进程的调用<b>都没有请求体</b>；
 * {@code MallApi#pageCommodity} 是第一个 {@code @PostExchange} + {@code @RequestBody}。
 *
 * <h3>🔴 所以这里起一个真的 HTTP server，断言线上真正发出去的那个头</h3>
 * 不去 mock 转换器列表：出问题的正是「列表里谁排在前面」这件事，
 * 而那是 {@code RestClient.build()} 之后才定下来的。只有真发一次请求才看得见。
 */
class DownstreamContentTypeTest {

    @Test
    @DisplayName("🔴 带 body 的下游 POST 必须是 application/json，不能是 yaml")
    void postBodyGoesOutAsJson() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/mall/commodity/page", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] response = "{\"list\":[],\"total\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            MallApi mallApi = new DownstreamClientConfig(appMapper())
                    .mallApi("http://127.0.0.1:" + server.getAddress().getPort());

            MallCommodityPageView view = mallApi.pageCommodity(
                    new MallCommodityPageCmd(null, null, "SORT", null, 1, 20));

            assertEquals(0L, view.total(), "响应也得能解析回来（走的是同一个 mapper）");
        } finally {
            server.stop(0);
        }

        assertNotNull(contentType.get(), "下游根本没收到请求？先看 base-url 与超时");
        assertTrue(contentType.get().startsWith("application/json"),
                "请求体被序列化成了 " + contentType.get() + " —— "
                        + "十有八九是 DownstreamClientConfig 里把 JSON 转换器挪出了原位（remove 之后 add 到队尾），"
                        + "队里排在它前面的 YAML 转换器接管了序列化。用 withJsonConverter 原位替换");
        assertTrue(body.get().startsWith("{"),
                "请求体不是 JSON 对象：" + body.get());
    }

    /** 与 {@link DownstreamJsonTest} 同一套：按 AppJsonConfig 的 customizer 造一个等价的 mapper */
    private static JsonMapper appMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new AppJsonConfig().appJsonCustomizer().customize(builder);
        return builder.build();
    }
}
