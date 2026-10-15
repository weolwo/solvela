package solvela.base.trace;

import org.slf4j.MDC;
import solvela.trace.MemberContract;

/**
 * 本次请求的会员号，放在 MDC 上。与 {@link DeviceTrace} 是同一套机制、同一个理由。
 *
 * <h3>它从哪来</h3>
 * 网关验过会话令牌之后把会员号写进 {@code X-Member-Id} 请求头，本进程的
 * {@code MemberTraceFilter} 把它读回 MDC。头名与合法性规则来自 {@link MemberContract}，
 * 两侧共用 —— 抄两份对不上时，活跃时间会静默不再刷新。
 *
 * <h3>🔴 不要拿它做授权</h3>
 * 见 {@link MemberContract} 的类注释：它的用途被刻意限制在「记一笔活跃时间」这类
 * 写了也不影响任何人的事情上。要判断「这个请求属于谁」，一律用命令里显式传下来的 memberId。
 *
 * <p>⚠️ 与 traceId 一样<b>不会传播到线程池</b>。
 *
 * @Date 2026-10-15
 */
public final class MemberTrace {

    private MemberTrace() {
    }

    /** 绑定会员号，返回值必须放进 try-with-resources */
    public static MDC.MDCCloseable open(Long memberId) {
        return MDC.putCloseable(MemberContract.MDC_KEY, memberId == null ? null : String.valueOf(memberId));
    }

    /**
     * 取当前请求的会员号；<b>没有返回 null，而且这是常态</b> ——
     * 匿名接口（登录、注册、发码）本来就没有会员号。
     */
    public static Long id() {
        return MemberContract.sanitize(MDC.get(MemberContract.MDC_KEY));
    }
}
