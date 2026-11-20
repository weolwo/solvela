package solvela.base.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import solvela.base.trace.DeviceTrace;
import solvela.trace.DeviceContract;

/**
 * 把网关传来的 {@code X-Device-Id} 读回 MDC，让下游的风控与日志够得着它。
 *
 * <p>它是设备号跨进程链路的<b>另一半</b>：
 * <ul>
 *   <li>调用方：网关的 {@code DownstreamClientConfig} 拦截器写这个头（验签通过之后）；</li>
 *   <li>被调方：本过滤器把它读回 MDC。</li>
 * </ul>
 * 少了任何一半，{@code t_promotion_config.device_limit} 就又变回一列死配置 ——
 * 后台能填、保存成功、列表里显示着，而运行时永远命中不了。
 *
 * <h3>🔴 本过滤器不做任何鉴别</h3>
 * 它只做形状校验（{@link DeviceContract#sanitize}），<b>不判断这个设备号是不是真的</b>。
 * 防伪发生在网关的 HMAC 验签那一步，本进程信任这个头的前提是
 * <b>它只接受来自网关的流量</b> —— 端口能被外网直接摸到的话，任何人都能编一个绕过限流。
 * 这是网络策略要保证的事，不是这里能解决的。
 *
 * <h3>没有就是没有，不生成</h3>
 * 与 {@link TraceFilter} 最大的不同：traceId 认不出来时自己生成一个是对的，
 * 设备号绝不能 —— 凭空造一个出来，限流会去数一个不存在的设备，
 * 而真正那台的计数永远是 0。见 {@link DeviceContract} 的类注释。
 *
 * <p>Order 比 {@link TraceFilter} 大 1：链路 id 先绑，这样本过滤器自己万一出问题，
 * 那条日志也是带 traceId 的。
 *
 * @Date 2026-09-09
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DeviceTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String deviceId = DeviceContract.sanitize(request.getHeader(DeviceContract.HEADER));
        if (deviceId == null) {
            // 不绑定：MDC 里没有这个键，DeviceTrace.id() 返回 null，下游按「没有设备」处理。
            // 🔴 也【不能】绑一个空串 —— 那会让 isEmpty 判断散在各处，而漏判一处
            // 就是拿空串去当设备号计数，所有没有设备的请求会共用同一个计数器
            chain.doFilter(request, response);
            return;
        }
        // 生命周期交给语言，不靠人记得写 finally —— 理由同 TraceFilter 的类注释。
        // 残留的危害在这里更实际：容器线程回池之后，下一个【没带设备号】的请求
        // 若不覆盖 MDC，就会挂着上一个请求的设备号去做限流计数
        try (MDC.MDCCloseable ignored = DeviceTrace.open(deviceId)) {
            chain.doFilter(request, response);
        }
    }
}
