package solvela.app.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import solvela.base.trace.Trace;
import solvela.marketing.api.ActivityApi;
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
public class DownstreamClientConfig {

    @Bean
    public MemberAuthApi memberAuthApi(@Value("${solvela.client.member.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(1), MemberAuthApi.class);
    }

    @Bean
    public ActivityApi activityApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(3), ActivityApi.class);
    }

    private static <T> T proxy(String baseUrl, Duration readTimeout, Class<T> apiType) {
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
                .build();

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(apiType);
    }
}
