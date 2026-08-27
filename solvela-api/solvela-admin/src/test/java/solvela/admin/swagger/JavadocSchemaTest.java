package solvela.admin.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 钉住「javadoc 当接口文档」这条链路：共享层的 DTO 身上已经<b>没有</b> {@code @Schema}，
 * 字段说明全靠 javadoc —— therapi 在编译期把注释编进 class 旁的资源，
 * springdoc 的 {@code SpringDocJavadocProvider} 在运行期读出来。
 *
 * <p>这条链路有三个各自独立、坏了都不报错的环节：
 * <ol>
 *   <li>maven-compiler-plugin 的 annotationProcessorPaths 里有没有 scribe；</li>
 *   <li>运行时 classpath 上有没有 therapi-runtime-javadoc
 *       （springdoc 那个配置类是 {@code @ConditionalOnClass(CommentFormatter)}）；</li>
 *   <li>{@code springdoc.enable-javadoc} 有没有被关掉。</li>
 * </ol>
 * 任何一环断了，接口文档只是<b>安静地变秃</b>：字段还在，描述没了，构建照样成功。
 * 所以必须有一个会失败的测试守着。
 *
 * <p><b>写 record 时注意</b>：springdoc 只认类注释里的 {@code @param}，<b>不认</b>组件前面的
 * {@code /** *}{@code /}。两种写法在源码里长得一样有用，但只有 @param 那份会进接口文档 ——
 * 这是实测出来的，不是文档里写的。组件注释仍然值得写（它就在字段旁边，改字段时不会漏看），
 * 但 @param 是<b>必须</b>的那一份。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JavadocSchemaTest {

    @LocalServerPort
    private int port;

    /** 故意不用 TestRestTemplate：Spring Boot 4 挪了它的包，而这个测试只需要一次 GET。 */
    private JsonNode schemas() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "取 /v3/api-docs 失败");
            return new ObjectMapper().readTree(resp.body()).path("components").path("schemas");
        } catch (Exception e) {
            throw new IllegalStateException("请求 /v3/api-docs 失败", e);
        }
    }

    private String desc(JsonNode schemas, String schema, String prop) {
        return schemas.path(schema).path("properties").path(prop).path("description").asText(null);
    }

    @Test
    @DisplayName("普通类 DTO：字段说明来自 javadoc，接口文档没退化")
    void 类字段的javadoc进了接口文档() {
        JsonNode schemas = schemas();
        assertFalse(schemas.isMissingNode(), "components.schemas 为空，说明 OpenAPI 根本没生成");

        // 这个 DTO 上一个 @Schema 都没有了，描述只可能来自 javadoc
        JsonNode target = schemas.path("MemberWalletStatDTO");
        assertFalse(target.isMissingNode(), "MemberWalletStatDTO 没进 OpenAPI，先确认它仍被某个接口返回");

        long described = 0;
        var props = target.path("properties").fields();
        while (props.hasNext()) {
            var e = props.next();
            if (e.getValue().hasNonNull("description")) {
                described++;
            }
        }
        assertTrue(described > 0,
                "MemberWalletStatDTO 的字段一个描述都没有 —— therapi 链路断了（见本类 javadoc 的三个环节）");
    }

    @Test
    @DisplayName("record 的组件说明也进得了接口文档 —— 两种写法都验一遍")
    void record组件的javadoc也进了接口文档() {
        JsonNode schemas = schemas();

        // 写法一：组件前 /** */ + 类注释里的 @param（ActivityDeleteCheckDTO 两种都有）
        assertEquals("是否允许删除", desc(schemas, "ActivityDeleteCheckDTO", "deletable"));

        // 写法二：只有组件前 /** */，没有 @param —— 这是共享层里其余 record 的写法，
        // 必须单独验：如果只有 @param 那条路通，其余 record 的文档就是秃的。
        assertEquals("引用数量", desc(schemas, "ActivityRefItem", "count"),
                "只写组件 javadoc、不写 @param 的 record 拿不到描述 —— "
                        + "那共享层其余 record 都得补 @param");
    }
}
