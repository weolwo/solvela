package solvela.app.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import solvela.base.trace.Trace;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 给每个请求一个链路 id，放进 MDC，同时回写响应头。
 *
 * <p>日志 pattern 里的 {@code %X{traceId}} 取的就是它；用户截图报障时，
 * 凭响应头里的这一串一次定位到日志。为什么这里非用 MDC 不可，见 {@link Trace} 的类注释。
 *
 * <h3>绑定的生命周期交给语言，不靠人记得写 finally</h3>
 * {@link Trace#open} 返回 {@code AutoCloseable}，配 try-with-resources 使用 ——
 * 编译器负责生成 finally。这和 {@code CurrentMember} 的 {@link ScopedValue}
 * 是同一个思路：让「作用域结束就失效」成为结构性质，而不是一条纪律。
 *
 * <p>写成 {@code MDC.put(...)} 加手写 {@code finally}（这个类的上一版）也是对的，
 * 但那是<b>可以忘的</b>，而忘了不会有任何报错。
 *
 * <p>⚠️ 残留的危害要说准，因为它和直觉不一样：<b>下一个请求不会读到旧值</b> ——
 * 它进来第一件事就是覆盖。这一条是实测的（把清理去掉后连打 40 次请求，
 * 40 个 traceId 依然各不相同）。真正出问题的是<b>请求之间</b>那段时间：
 * 容器线程回到池里之后，任何在这根线程上打的日志都会挂着上一个请求的 traceId，
 * 指向一条毫不相干的调用链 —— 这种日志比没有日志更误导人，因为它看起来完全正常。
 *
 * <p>{@code TraceFilterTest} 直接对着「返回后线程必须干净」这个契约断言，
 * 正常路径与异常路径各一条。
 *
 * <h3>🔴 客户端传来的 id 必须校验</h3>
 * 允许客户端指定 traceId 是有价值的（App 侧能把自己的埋点和服务端日志串起来），
 * 但直接采信就是一个<b>日志注入</b>口子：值里塞一段编码过的换行加一行伪造日志，
 * 日志文件里就会出现一条看起来完全正常、实际是别人写的记录。采集到 ELK 之后，
 * 谁也分不出哪条是真的。
 *
 * <p>所以只接受「32 位以内的字母数字和连字符」。不合规就当没传、自己生成一个 ——
 * 不报错，因为这不是用户该关心的事。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    private static final int MAX_LENGTH = 32;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = sanitize(request.getHeader(Trace.KEY));
        response.setHeader(Trace.KEY, traceId);

        try (MDC.MDCCloseable ignored = Trace.open(traceId)) {
            chain.doFilter(request, response);
        }
    }

    private static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return generate();
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-';
            if (!allowed) {
                return generate();
            }
        }
        return candidate;
    }

    private static String generate() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
    }
}
