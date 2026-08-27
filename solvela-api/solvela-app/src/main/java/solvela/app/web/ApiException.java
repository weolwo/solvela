package solvela.app.web;

/**
 * 业务异常。抛出去，由 {@link ApiExceptionHandler} 翻成 HTTP 响应。
 *
 * <h3>为什么是抛异常，不是返回 ResponseDTO</h3>
 * 上一版的 service 返回 {@code ResponseDTO<T>}，于是每个调用方都要判一次
 * {@code result.getOk()}，判漏了就当成功往下走 —— 而漏判没有任何提示。
 * 更要命的是它让<b>领域层的返回类型变成了一个 HTTP 响应信封</b>：
 * 定时任务、消息消费、将来的 RPC 调同一个方法，拿到的是一个装着 code 和 msg 的对象。
 *
 * <p>异常没有这个问题：不处理就往上抛，不会被静默忽略；领域方法的返回类型
 * 就是它真正产出的东西。
 *
 * <p>⚠️ 代价是「异常用于控制流」的性能顾虑。这里不填栈 —— 见 {@link #fillInStackTrace}。
 */
public class ApiException extends RuntimeException {

    private final ApiErrors error;

    public ApiException(ApiErrors error) {
        this(error, error.defaultMessage());
    }

    /**
     * 带具体文案。这句话<b>会原样给到用户</b>，所以不要往里放 SQL、类名、堆栈或 id 之类的内部信息。
     */
    public ApiException(ApiErrors error, String message) {
        super(message, null, false, false);
        this.error = error;
    }

    public ApiErrors error() {
        return error;
    }

    /**
     * 不采集栈。
     *
     * <p>业务异常是<b>预期内</b>的结果（密码错了、活动结束了），在 C 端的 QPS 下会非常频繁。
     * {@code fillInStackTrace} 是 JVM 里最贵的操作之一，而这些异常的栈没人会去看 ——
     * 定位问题靠的是 code 和 traceId。真正的服务端故障走 {@code Exception} 那条路，栈照常保留。
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
