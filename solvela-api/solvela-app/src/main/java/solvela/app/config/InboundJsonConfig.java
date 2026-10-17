package solvela.app.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * C 端请求体的 JSON 解析：<b>严格模式</b>（网关方案 §5.6）。只管 Controller 收的请求体。
 *
 * <h3>严在哪</h3>
 * <ul>
 *   <li><b>不认识的字段报错</b>：前端把 {@code phone} 拼成 {@code phoneNum}，得到的是「未知字段 phoneNum」，
 *       而不是一句莫名其妙的「请输入手机号」。请求对象本身就是参数白名单（不另建白名单表），
 *       这一条让它真的是白名单。翻成 400 并记一条低置信事件见 {@code ApiExceptionHandler}；</li>
 *   <li><b>重复的键报错</b>：Jackson 默认取最后一个。{@code {"identity":"a","identity":"b"}} 在不同的解析器里
 *       可能读出两个答案 —— 安检门扫的、日志记的、Controller 绑的未必是同一个值；</li>
 *   <li><b>结构封顶</b>：Jackson 默认单个字符串 100MB、嵌套 500 层、数字 1000 位，一个请求就能让网关解析半天。
 *       整份请求体按 {@link #MAX_DOCUMENT_BYTES} 封顶，与 nginx 给 C 端 {@code /api} 的上限相同 ——
 *       那一道在 nginx，这一道管没有 nginx 的环境，也管 nginx 配置被改宽的那一天。</li>
 * </ul>
 * 字段级的长度（每个字符串多长）由请求对象上的 {@code @Size} 管，见 {@code InputLimits}。
 *
 * <h3>🔴 只有这里严，其它地方保持宽松</h3>
 * Spring 的 {@code JsonMapper} bean 不动：网关拿它<b>解析 app-biz 的响应</b>（{@code DownstreamClientConfig}），
 * Redis 缓存、规则快照也各有各的 mapper。那些地方要容忍新旧版本并存 —— biz 先发版多了一个字段，
 * 网关不能因此整个接口报错。严格只对「外面进来的」。
 *
 * <h3>兼容规则</h3>
 * 加字段：先发后端、再发前端。删字段：请求对象上用 {@code @JsonIgnoreProperties({"旧字段"})} 继续接受一段时间 ——
 * 今天前后端同仓同发风险很小；将来有了原生 App，旧版本会在用户手机上存在很久，这一步必须做。
 *
 * <h3>为什么自己按 Boot 的套路造一个，而不是 {@code jsonMapper.rebuild()}</h3>
 * 结构上限挂在 {@link JsonFactory} 上，而 rebuild 出来的构建器换不了 factory。所以从 Boot 的那个 factory 派生一个，
 * 再把容器里全部 {@link JsonMapperBuilderCustomizer}（时间格式、Long / 金额的序列化，见 {@link AppJsonConfig}）
 * 照 Boot 自己的顺序套一遍 —— 响应的形状与 Boot 那个 mapper 完全相同，只是读得更严。
 *
 * @Date 2026-10-17
 */
@Configuration
public class InboundJsonConfig implements WebMvcConfigurer {

    /** 整份请求体最多多少字节。C 端最大的合法请求体（收货地址、滑块轨迹）不到 10KB */
    public static final int MAX_DOCUMENT_BYTES = 64 * 1024;

    /** 单个字符串值最长多少字符。字段自己的 {@code @Size} 远比它小，这是解析阶段的天花板 */
    public static final int MAX_STRING_LENGTH = 16 * 1024;

    /** 最深几层。最深的合法请求是滑块轨迹（两层数组） */
    public static final int MAX_NESTING_DEPTH = 16;

    /** 数字最长几位。金额、Long 都远到不了 */
    public static final int MAX_NUMBER_LENGTH = 32;

    /** 字段名最长几个字符 */
    public static final int MAX_NAME_LENGTH = 128;

    private final JsonMapper inbound;

    public InboundJsonConfig(JsonFactory jsonFactory, ObjectProvider<JsonMapperBuilderCustomizer> customizers) {
        this.inbound = inboundMapper(jsonFactory, customizers.orderedStream().toList());
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        // 🔴 原位替换（withJsonConverter），不是 remove + add —— 转换器的先后决定了谁接 JSON
        builder.withJsonConverter(new JacksonJsonHttpMessageConverter(inbound));
    }

    static JsonMapper inboundMapper(JsonFactory base, List<JsonMapperBuilderCustomizer> customizers) {
        JsonFactory factory = base.rebuild()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxDocumentLength(MAX_DOCUMENT_BYTES)
                        .maxStringLength(MAX_STRING_LENGTH)
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .maxNumberLength(MAX_NUMBER_LENGTH)
                        .maxNameLength(MAX_NAME_LENGTH)
                        .build())
                .build();
        JsonMapper.Builder builder = JsonMapper.builder(factory);
        customizers.forEach(customizer -> customizer.customize(builder));
        // 在 customizer 之后设：配置文件里的 fail-on-unknown-properties: false 不能把这里改回去
        return builder
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
    }
}
