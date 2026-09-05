package solvela.app.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import solvela.app.web.Trace;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.PrizeRecordApi;
import solvela.member.api.AssetApi;
import solvela.member.api.MemberAuthApi;

import java.time.Duration;

/**
 * 网关到两个下游服务的 HTTP 客户端。
 *
 * <h3>整套设计在这里收口</h3>
 * 网关的业务代码只认识 {@link MemberAuthApi} / {@link ActivityApi} 两个接口。
 * 它们此前由同进程的 bean 实现，现在换成这里生成的 HTTP 代理 ——
 * <b>{@code MemberLoginService}、{@code MemberPrincipalLoader} 一行都没改</b>。
 * 这就是当初把契约与实现分开、让接口自带 {@code @HttpExchange} 要换来的东西。
 *
 * <h3>超时按调用的性质分开设</h3>
 * <ul>
 *   <li><b>会员服务</b>：认证与取身份都是主键点查，1 秒足够。
 *       它在<b>每个请求</b>的关键路径上（取身份），拖长了就是整站变慢；</li>
 *   <li><b>营销服务</b>：抽奖要跑脚本、扣库存、落流水，3 秒。</li>
 * </ul>
 * 🔴 不设超时是最危险的默认值：JDK 默认永不超时，下游卡住时网关的线程会一直挂着，
 * 挂到线程池耗尽 —— 表现是「整站没响应」，而根因在另一个进程里。
 *
 * <h3>链路 id 走请求头</h3>
 * 见方案 §3：traceId 不进任何 DTO。两个进程的日志因此能用同一个 id 串起来，
 * 而契约里没有一个字段为它存在。
 */
@Configuration
@RequiredArgsConstructor
public class DownstreamClientConfig {

    @Bean
    public MemberAuthApi memberAuthApi(@Value("${solvela.client.member.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(1), MemberAuthApi.class);
    }

    /**
     * 资产。<b>1 秒</b>，与会员服务同一档 —— 它是按 member_id 的主键点查，
     * 而且挂在「我的」页首屏上，拖长了就是用户盯着一个空白的余额。
     *
     * <p>契约在 {@code solvela-member-api}：资产将来和会员同属 app-member 服务，
     * 所以共用那一份契约（那个 pom 里写着「不会再有 solvela-ledger-api」）。
     * 今天它和营销跑在同一个 app-biz 进程里，所以 base-url 复用 marketing 那个；
     * 真拆进程的那天，改的就是<b>这一行</b>的配置键。
     */
    @Bean
    public AssetApi assetApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(1), AssetApi.class);
    }

    /**
     * 奖励记录。<b>3 秒</b>，与营销同一档：它查的是 t_prize_log，
     * 一个老用户可能有上千条，比主键点查重。
     *
     * <p>契约在 {@code solvela-marketing-api}（奖品归它，见那个 pom 的 description），
     * 实现在 solvela-prize，今天和营销同进程，所以复用同一个 base-url。
     */
    @Bean
    public PrizeRecordApi prizeRecordApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(3), PrizeRecordApi.class);
    }

    /**
     * 商城。<b>3 秒</b>：商品列表要分页 + 聚合库存与收藏，比主键点查重。
     *
     * <p>契约在 {@code solvela-marketing-api}（商城与玩法将来同进程），
     * 实现在 solvela-mall，今天与营销同进程，所以复用同一个 base-url。
     */
    @Bean
    public MallApi mallApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(3), MallApi.class);
    }

    @Bean
    public ActivityApi activityApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(3), ActivityApi.class);
    }

    /**
     * 🔴 <b>下游客户端必须用应用自己的 JsonMapper，不能用 RestClient 的默认转换器。</b>
     *
     * <p>2026-09-05 踩到：{@code listOpenActivities} 是第一个把 {@code LocalDateTime}
     * 跨进程传回来的契约，网关解析时炸了 ——
     * {@code Failed to deserialize java.time.LocalDateTime from String "2026-08-30 00:00:00"}。
     *
     * <p>原因是 {@link AppJsonConfig} 那份 {@code yyyy-MM-dd HH:mm:ss} 的配置
     * 只作用于 Spring MVC 的 mapper（它走 {@code JsonMapperBuilderCustomizer}），
     * 而 {@code RestClient.builder()} 会<b>自己造一套默认消息转换器</b>，
     * 里面的 mapper 只认 ISO-8601 的 {@code 2026-08-30T00:00:00}。
     *
     * <p>所以这里把容器里那个已经配好的 mapper 显式塞进去。
     * <b>别改成「让契约传字符串」绕过去</b>：那等于每个 record 都要自己定时间格式，
     * 而 {@code ActivityRuleView} 这类已经在传 LocalDateTime 了。
     *
     * <h3>🔴 替换要原位替换，别 remove 完再 add 到队尾</h3>
     * 第一版写的是 {@code messageConverters(list -> { list.removeIf(...); list.add(...); })}，
     * 于是 JSON 转换器<b>掉到了队尾</b>。RestClient 写请求体时是按顺序找第一个
     * {@code canWrite} 的转换器，队里排在 JSON 之前的是 YAML ——
     * Spring 7 的默认顺序是 json → smile → cbor → <b>yaml</b> → xml，
     * 而 {@code com.fasterxml.jackson.dataformat.yaml.YAMLFactory} 在 classpath 上
     * （swagger-core-jakarta 带进来的），所以 YAML 转换器是真的被注册了。
     *
     * <p>结果：所有<b>带 body 的 POST</b>（{@code MallApi#pageCommodity} 是第一个）
     * 被序列化成 {@code Content-Type: application/yaml}，下游 400/500 回来一句
     * {@code HttpMediaTypeNotSupportedException: Content-Type 'application/yaml' is not supported}。
     * GET 没 body，所以之前一直没暴露。
     *
     * <p>{@code withJsonConverter} 是原位换掉那个槽位，顺序不动，这是它存在的理由。
     */
    private final JsonMapper jsonMapper;

    private <T> T proxy(String baseUrl, Duration readTimeout, Class<T> apiType) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(readTimeout);

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String traceId = Trace.id();
                    if (traceId != null) {
                        request.getHeaders().add(Trace.KEY, traceId);
                    }
                    return execution.execute(request, body);
                })
                // 用应用配好的那个 mapper（时间格式、Long/BigDecimal 序列化都在里面），
                // 不用 RestClient 自己造的默认转换器 —— 见上面那两段。
                // 🔴 原位替换（withJsonConverter），不是 remove + add
                .configureMessageConverters(converters ->
                        converters.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .build();

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(apiType);
    }
}
