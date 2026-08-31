package solvela.marketing.server.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import solvela.base.trace.Trace;
import solvela.member.api.MemberProposalApi;

import java.time.Duration;

/**
 * 会员服务的 HTTP 客户端。
 *
 * <h3>这就是整套契约设计要兑现的地方</h3>
 * {@link MemberProposalApi} 是一个<b>只有接口的契约</b>：
 * <ul>
 *   <li>在 admin（单体）里，它由 {@code ProposalApiService} 这个本地 bean 实现；</li>
 *   <li>在营销服务里，它由下面这几行生成的 <b>HTTP 代理</b>实现。</li>
 * </ul>
 * 而调用方（四个发奖 handler）<b>一行代码都不用改</b> —— 它们只认识接口。
 * 这是当初把契约与实现分开、并且让接口自带 {@code @HttpExchange} 的全部理由。
 *
 * <h3>超时必须显式设</h3>
 * 不设的话 JDK 默认是<b>永不超时</b>：会员服务卡住时，营销侧的发奖线程会一直挂着，
 * 挂到线程池耗尽 —— 表现是「整个营销服务没响应」，而根因在另一个进程里。
 *
 * <p>读超时给 3 秒而不是文档里读接口的 800ms：这条链路上会员服务要做风控责任链、
 * 落提案、可能还要同步入账，本来就比一次查询慢。
 * <b>它的失败是可接受的</b> —— 超时会让发奖流水停在「待提交」，由重投任务再来一次。
 *
 * <h3>链路 id 靠请求头透传</h3>
 * 见方案 §3：traceId 不进任何 DTO，走传输层。两个服务的日志因此能用同一个 id 串起来。
 */
@Configuration
public class MemberServiceClientConfig {

    @Bean
    public MemberProposalApi memberProposalApi(
            @Value("${solvela.client.member.base-url}") String baseUrl) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

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
                .createClient(MemberProposalApi.class);
    }
}
