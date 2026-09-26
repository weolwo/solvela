package solvela.app.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import solvela.auth.device.DeviceIdentity;
import solvela.auth.device.DeviceTokenCodec;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备识别：把请求头里的设备令牌验签成设备身份，绑进 {@link CurrentDevice} 的作用域。
 *
 * <h3>只识别，不拦截 —— 与 {@link AuthenticationFilter} 同一条线</h3>
 * 令牌没带、验不过、模式是 enforce……本过滤器<b>一律放行</b>，
 * 要不要拒绝由 {@link DeviceRequirementInterceptor} 在 MVC 里决定。
 *
 * <p>分开不是洁癖，是两件事的判据不同：识别只看请求头，拒绝要看<b>被调用的是哪个方法</b>
 * （{@code /device/register} 自己就不能要求设备令牌），而「哪个方法」要等 Spring MVC
 * 完成 handler 映射才知道 —— 过滤器这一层拿不到。
 *
 * <p>还有一个更实际的理由：在过滤器里拒绝，就得自己拼一份 JSON 错误体，
 * 于是错误格式有了第二个来源。交给拦截器抛 {@code ApiException}，
 * 全站错误响应仍然只有 {@code ApiExceptionHandler} 一个出口。
 *
 * <h3>为什么排在 AuthenticationFilter 之前</h3>
 * 设备身份比会员身份<b>更外层</b>：匿名请求（注册、登录、活动页）也有设备，
 * 而防刷要防的恰恰是这些接口。排在后面就意味着匿名路径拿不到设备上下文，等于没做。
 *
 * <h3>零 IO</h3>
 * 一次 HMAC 计算，不查 Redis、不查库 —— 这正是当初选自包含令牌而不是 Redis 令牌的原因：
 * 本过滤器在<b>每一个</b>请求上跑，包括所有匿名请求，而会员令牌只在已登录请求上解析。
 *
 * @Date 2026-09-08
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class DeviceFilter extends OncePerRequestFilter {

    /** 每这么多个请求汇报一次覆盖率。取整万是为了让日志稀疏到可以长期开着。 */
    private static final long REPORT_EVERY = 10_000L;

    private final DeviceTokenCodec deviceTokenCodec;
    private final DeviceAuthProperties properties;

    private final RequestCredentials credentials;

    /**
     * observe 档的两个计数器。
     *
     * <p>用进程内计数而不是逐请求打日志：一个网关每天几百万请求，
     * 「每个没带令牌的请求打一行」会让日志没法看，而真正要的只是<b>一个比例</b>。
     *
     * <p>⚠️ 这是权宜之计。有了指标系统之后应该换成 counter，
     * 那样才能按端、按版本、按时间切片看 —— 而「H5 的覆盖率是不是特别低」
     * 恰恰是最该问的问题之一。累计值不清零，重启即归零，这对判断趋势够用了。
     */
    private final AtomicLong seen = new AtomicLong();

    private final AtomicLong withDevice = new AtomicLong();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        DeviceIdentity identity = resolve(request);

        if (properties.mode() == DeviceAuthProperties.Mode.OBSERVE) {
            record(identity != null);
        }

        if (identity == null) {
            // 没有设备身份。ScopedValue 不接受 null 值，所以不绑定 ——
            // 于是 CurrentDevice.isBound() 就是「有没有可信设备」的准确答案，不需要再判空。
            chain.doFilter(request, response);
            return;
        }

        try {
            ScopedValue.where(CurrentDevice.DEVICE, identity).call(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (ServletException | IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Carrier.call 的签名把受检异常一并抛出，这里做类型收敛。
            // doFilter 只会抛上面那三类，走到这里说明 JDK 的签名比实际更宽。
            throw new ServletException(e);
        }
    }

    private DeviceIdentity resolve(HttpServletRequest request) {
        // 请求头（App）或 HttpOnly cookie（Web），见 RequestCredentials
        String raw = credentials.deviceValue(request);
        if (raw == null) {
            return null;
        }
        // 验不过返回 null，不打日志：老版本客户端、清了缓存、有人在扫接口 —— 都是常态。
        // 「有多少请求的令牌验不过」是个指标，由下面的计数器按比例给，不靠逐条日志。
        return deviceTokenCodec.verify(raw.trim());
    }

    /**
     * 记一次，并在整万次时把覆盖率打进日志。
     *
     * <p>比例算的是<b>累计值</b>而不是最近一万次：客户端发版是个持续爬坡的过程，
     * 累计值虽然滞后，但不会因为某一分钟的流量结构（比如一批 H5 活动流量涌进来）
     * 出现看起来像是回退的抖动。
     */
    private void record(boolean hasDevice) {
        if (hasDevice) {
            withDevice.incrementAndGet();
        }
        long total = seen.incrementAndGet();
        if (total % REPORT_EVERY == 0) {
            long hit = withDevice.get();
            log.info("[Device] 设备令牌覆盖率 {}/{} ({}%)。切 enforce 之前要看到它稳定 —— "
                            + "爬不上去多半是客户端有 bug，不是用户升级得慢",
                    hit, total, String.format("%.1f", hit * 100.0 / total));
        }
    }
}
