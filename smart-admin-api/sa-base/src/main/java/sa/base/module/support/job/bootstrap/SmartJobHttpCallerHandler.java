package sa.base.module.support.job.bootstrap;

import lombok.extern.slf4j.Slf4j;
import sa.base.common.util.JsonUtils;
import sa.base.module.support.job.config.SmartJobConfig;
import sa.base.module.support.job.constant.SmartJobLaneEnum;
import sa.base.module.support.job.core.SmartJob;
import sa.base.module.support.job.core.SmartJobContext;
import sa.base.module.support.job.core.JobParam;
import sa.base.module.support.job.core.SmartJobHandler;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 内置任务：定时调用一个内网 HTTP 接口。
 *
 * <p>解决的是一类真实且高频的诉求：「每天凌晨调一下某个内网 API 触发状态刷新」——
 * 为此新建一个 Java 类、走一遍发版流程，不划算。
 *
 * <p>🔴🔴 <b>但它等于在后台开了一个 SSRF 入口</b>：任何有定时任务配置权限的人，
 * 都能让服务器去请求任意地址（包括云厂商的元数据服务、内部管理接口）。
 * 所以配了四道闸门，缺一不可：
 * <ol>
 *   <li><b>目标白名单</b>（{@code smart.job.http-allow-hosts}）配在 yaml 里，
 *       <b>后台改不了</b> —— 白名单若能在后台维护，它就不是闸门了；</li>
 *   <li>只允许 {@code http} / {@code https}，禁 {@code file://} / {@code gopher://} 等；</li>
 *   <li>强制连接与读取超时；</li>
 *   <li>响应体截断，别把 10MB 的响应塞进 {@code result_summary}。</li>
 * </ol>
 *
 * <p>🔴 <b>定位写死：运维探活与触发，不承载业务逻辑。</b>
 * 一旦有了它，最大的风险不是 SSRF 而是<b>滥用</b> ——
 * 「什么都用 HTTP 任务糊一下」会让业务逻辑散落进后台配置里，
 * 既没有版本管理也没有 code review，那正是配置化脚本最坏的那种形态。
 * 所以 {@code group = OPS}，且默认<b>不开启</b>（白名单为空即拒绝一切调用）。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
@Component
@SmartJobHandler(
        name = "httpCaller",
        title = "【运维】定时调用内网 HTTP 接口",
        group = "OPS",
        lane = SmartJobLaneEnum.FAST,
        idempotent = false,
        defaultTimeoutSeconds = 30,
        params = {
                @JobParam(key = "url", desc = "目标地址（必须在 yaml 白名单内）",
                        type = JobParam.Type.STRING, required = true),
                @JobParam(key = "method", desc = "请求方法", type = JobParam.Type.ENUM,
                        defaultValue = "GET", options = {"GET", "POST", "PUT", "DELETE"}),
                @JobParam(key = "headers", desc = "请求头（JSON 对象）", type = JobParam.Type.STRING),
                @JobParam(key = "body", desc = "请求体", type = JobParam.Type.STRING)
        }
)
public class SmartJobHttpCallerHandler implements SmartJob {

    /**
     * 响应体截断长度：摘要列宽 512，这里留足余量后再由框架截一次
     */
    private static final int RESPONSE_MAX_LENGTH = 2048;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private final SmartJobConfig jobConfig;

    private final HttpClient httpClient;

    public SmartJobHttpCallerHandler(SmartJobConfig jobConfig) {
        this.jobConfig = jobConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // 🔴 不跟随重定向：跟随会让白名单形同虚设 ——
                //    白名单内的地址可以 302 到任意位置，闸门就被绕过去了
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 参数为 JSON：{@code {"url":"...","method":"GET","headers":{...},"body":"..."}}
     */
    private record HttpCallParam(String url, String method, Map<String, String> headers, String body) {
    }

    @Override
    public String execute(SmartJobContext ctx) throws Exception {
        HttpCallParam param = JsonUtils.parseType(ctx.param(), new TypeReference<HttpCallParam>() {
        });
        if (null == param || null == param.url() || param.url().isBlank()) {
            throw new IllegalArgumentException("参数格式错误，应为 {\"url\":\"...\",\"method\":\"GET\"}");
        }

        URI uri = URI.create(param.url().trim());
        this.checkGate(uri);

        String method = null == param.method() ? "GET" : param.method().trim().toUpperCase(Locale.ROOT);
        HttpRequest.BodyPublisher bodyPublisher = (null == param.body() || param.body().isBlank())
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(param.body());

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(READ_TIMEOUT)
                .method(method, bodyPublisher);
        if (null != param.headers()) {
            param.headers().forEach(builder::header);
        }

        ctx.checkCancelled();
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String body = null == response.body() ? "" : response.body();
        if (body.length() > RESPONSE_MAX_LENGTH) {
            body = body.substring(0, RESPONSE_MAX_LENGTH) + "...(已截断)";
        }
        // 非 2xx 视为失败：调用方配这个任务是为了「触发成功」，
        // 静默把 500 当成功会让这个任务变成一个永远绿灯的摆设
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " → " + body);
        }
        return "HTTP " + response.statusCode() + " → " + body;
    }

    /**
     * 四道闸门中的前两道（超时与截断在调用处）。
     */
    private void checkGate(URI uri) {
        String scheme = null == uri.getScheme() ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("只允许 http/https 协议，当前为：" + scheme);
        }
        String host = uri.getHost();
        if (null == host || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少 host");
        }
        List<String> allowHosts = jobConfig.getHttpAllowHosts();
        if (null == allowHosts || allowHosts.isEmpty()) {
            // 🔴 默认拒绝一切：白名单没配就说明没人评估过风险，
            //    这时候「放行」是最坏的默认值
            throw new IllegalStateException(
                    "未配置 smart.job.http-allow-hosts 白名单，HTTP 任务默认不可用");
        }
        boolean allowed = allowHosts.stream().anyMatch(allow -> {
            String a = allow.trim().toLowerCase(Locale.ROOT);
            // 支持 .example.com 形式的后缀匹配；其余为精确匹配，不做通配以免写出过宽的规则
            return a.startsWith(".") ? host.toLowerCase(Locale.ROOT).endsWith(a) : host.equalsIgnoreCase(a);
        });
        if (!allowed) {
            throw new IllegalStateException("目标 host 不在白名单内：" + host);
        }
    }
}
