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
import solvela.app.auth.CurrentDevice;
import solvela.app.web.Trace;
import solvela.trace.DeviceContract;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.RechargeApi;
import solvela.marketing.api.PrizeRecordApi;
import solvela.member.api.AssetApi;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.DeliveryApi;
import solvela.member.api.DeviceApi;
import solvela.member.api.ProposalRecordApi;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.MemberEntitlementApi;
import solvela.member.api.MemberGradeApi;
import solvela.member.api.MemberSignApi;
import solvela.member.api.NotificationApi;

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
     * 设备身份。<b>1 秒</b>，与会员认证同一档：签发是一次 Redis 计数加一条 insert，
     * 比主键点查重不了多少，而它挂在<b>客户端冷启动</b>的路径上 ——
     * 拖长了就是用户盯着启动页。
     *
     * <p>契约在 {@code solvela-member-api}，与 {@link MemberAuthApi} 共用会员服务的 base-url。
     * 设备将来要和会员一起独立出去（见 solvela-app-biz 的 pom 注释），到那天改的是这一行的配置键。
     *
     * <p>⚠️ 超时了不要重试：签发不是幂等的，重试一次就多一台设备、多烧一次 IP 配额。
     * 客户端拿到 5xx 应当让用户重开 App，而不是自动重试。
     */
    @Bean
    public DeviceApi deviceApi(@Value("${solvela.client.member.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(1), DeviceApi.class);
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
     * 券包与选券试算。<b>2 秒</b>：券包是按 member_id 的索引扫描，
     * 比余额那种主键点查重，但比商品列表轻。
     *
     * <p>🔴 这里只代理<b>只读</b>的 {@link CouponQueryApi}。
     * 核销那一半（{@code CouponWriteOffApi}）能直接消耗用户的券，
     * 网关这一侧<b>刻意连代理 bean 都没有</b> —— 券什么时候被用掉，
     * 只能由下单那条链路决定，不能由公网入口决定。
     *
     * <p>契约在 {@code solvela-member-api}：券是资产，将来和会员同属 app-member 服务。
     * 今天它和营销跑在同一个 app-biz 进程里，所以 base-url 复用 marketing 那个。
     */
    @Bean
    public CouponQueryApi couponQueryApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(2), CouponQueryApi.class);
    }

    /**
     * 充话费（外部场景消费）。<b>3 秒</b>：下单要试算券 + 锁券，比主键点查重。
     *
     * <p>⚠️ 今天运营商那一端是<b>假的</b>，而且假充值配到生产会让 app-biz
     * <b>启动失败</b>（{@code ExternalRechargeService.checkTransport}）——
     * 那道闸在域里，网关这一侧不做任何环境判断。
     *
     * <p>契约在 {@code solvela-marketing-api}：外部场景和商城将来同属
     * app-activity 那个服务，所以共用同一个 base-url。
     */
    @Bean
    public RechargeApi rechargeApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(3), RechargeApi.class);
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

    /**
     * 优惠记录（提案记录）。<b>3 秒</b>：一次带条件的列表查询。
     *
     * <p>🔴 注册的是<b>只读</b>的 {@link ProposalRecordApi}，
     * 不是带 {@code createProposal} 的 {@code MemberProposalApi} ——
     * 那个能造一笔发放，不该在公网入口的容器里存在一个可注入的 bean。
     * 与 {@link AssetApi} / {@code AssetDebitApi} 那对是同一条规矩。
     *
     * <p>实现在 solvela-risk，今天与营销同进程，所以复用同一个 base-url。
     */
    @Bean
    public ProposalRecordApi proposalRecordApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(3), ProposalRecordApi.class);
    }

    /**
     * 消息中心。契约在 {@code solvela-member-api}（通知是挂在会员身上的东西），
     * 但实现今天在 app-biz，所以 base-url 复用 member 那个 —— 两个键现在都指向同一个进程。
     *
     * <p>2 秒：收件箱是一次带分页的索引查询加一次 count，比主键点查重，
     * 但它挂在用户点开消息中心的路径上，拖长了就是转圈。
     */
    @Bean
    public NotificationApi notificationApi(@Value("${solvela.client.member.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(2), NotificationApi.class);
    }

    @Bean
    public ActivityApi activityApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(3), ActivityApi.class);
    }

    /**
     * 每日签到。<b>1 秒</b>，与会员认证同一档：一次 Redis 自增加一次会员存在性点查，
     * 比主键点查重不了多少，而它挂在用户点按钮的路径上 —— 拖长了就是按钮一直转圈。
     *
     * <p>⚠️ 签到会顺带广播一个业务动作，由营销侧翻译成任务进度，
     * 但那一段是<b>异步</b>的（{@code task-event-executor}），不占这 1 秒。
     * 所以这里不需要按"营销"那一档给 3 秒 —— 真给了，反而会在任务系统抖动时
     * 让签到接口陪着一起慢。
     *
     * <p>契约在 {@code solvela-member-api}：签到是挂在会员身上的行为，
     * 所以走会员服务的 base-url，与 {@link MemberAuthApi} 同一个键。
     */
    /**
     * 实物履约单（我的实物奖品）。<b>2 秒</b>：按 member_id 的索引扫描，
     * 实物单量天然很小，比券包还轻。
     *
     * <p>契约在 {@code solvela-member-api}：履约单是资产，将来和会员同属
     * app-member 服务。今天它和营销跑在同一个 app-biz 进程里，
     * 所以 base-url 复用 marketing 那个 —— 与 {@link AssetApi} / {@link CouponQueryApi} 一致。
     *
     * <p>🔴 这里代理的是<b>整个</b> {@link DeliveryApi}，含写方法 {@code fillReceiver}。
     * 那和券那边只代理只读的 {@code CouponQueryApi} 不同，理由是：
     * 补填收货信息<b>本来就该由用户发起</b>，它不像核销那样能消耗资产。
     * 但网关<b>不直接调</b> {@code fillReceiver} —— C 端那条路走
     * {@code MallApi.fillDeliveryAddress}（只有商城解析得了 addressId），
     * 这个 bean 存在是为了将来（比如客服端代填）有一条明确的路，而不是让人临时造一条。
     */
    @Bean
    public DeliveryApi deliveryApi(@Value("${solvela.client.marketing.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(2), DeliveryApi.class);
    }

    /**
     * 会员等级 —— <b>只读</b>。
     *
     * <p>契约里一个写方法都没有，是刻意的：等级是派生状态，跟着成长值走。
     * 给 C 端开写口等于给刷等级开门。人工调级只在管理端。
     *
     * <p>base-url 走 member 那个：等级表属于会员域（{@code t_member_grade} /
     * {@code t_member_growth}），与 {@code MemberSignApi} 同一侧 ——
     * 而不是像履约单那样跟着资产走。
     *
     * <p>超时给 2 秒而不是 1：等级页一次要带回完整阶梯与每一档的权益，
     * 比签到那种单点写重一些。
     */
    @Bean
    public MemberGradeApi memberGradeApi(@Value("${solvela.client.member.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(2), MemberGradeApi.class);
    }

    /**
     * 我的权益：列表是只读，<b>领取那一步会真的发出东西</b>（调资产域发放）。
     *
     * <p>⚠️ 超时给 5 秒而不是 2：领取那一步下游要走一次资产发放，
     * 比纯查询重。超时太短的后果不是「慢」，是<b>发放已经成功而网关先超时了</b> ——
     * 用户看到失败，再点一次。所幸发放单号在生成时就定死，重试不会多发。
     */
    @Bean
    public MemberEntitlementApi memberEntitlementApi(@Value("${solvela.client.member.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(5), MemberEntitlementApi.class);
    }

    @Bean
    public MemberSignApi memberSignApi(@Value("${solvela.client.member.base-url}") String baseUrl) {
        return proxy(baseUrl, Duration.ofSeconds(1), MemberSignApi.class);
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
                    // 设备号同走请求头，不进任何 DTO —— 它要一路带到发奖风控，
                    // 中间经过活动、抽奖、奖品派发、提案四层，每层 DTO 都加字段
                    // 等于开四个「忘了填就静默失效」的口子。见 DeviceContract 类注释。
                    //
                    // 🔴 这里传的是【验签通过的设备号】，不是客户端发来的设备令牌。
                    // 令牌绝不能原样透传下去 —— 那等于把凭证散给所有内部服务。
                    String deviceId = CurrentDevice.deviceIdOrNull();
                    if (deviceId != null) {
                        request.getHeaders().add(DeviceContract.HEADER, deviceId);
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
