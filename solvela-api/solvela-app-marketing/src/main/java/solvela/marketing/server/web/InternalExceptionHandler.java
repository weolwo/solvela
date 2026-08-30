package solvela.marketing.server.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import solvela.base.trace.Trace;
import solvela.exception.BusinessException;

/**
 * 营销服务<b>唯一</b>的错误出口。
 *
 * <h3>为什么服务间调用也要有真实状态码</h3>
 * 网关侧的 {@code RestClient} 是按状态码决定「这次调用成没成」的。
 * 一律返回 200 的话，网关只能去解析 body 才知道出没出事，
 * 而它的重试策略、熔断、APM 成功率全部失效 —— 与 C 端不要信封是同一个理由。
 *
 * <h3>预期内的失败根本不该走到这里</h3>
 * 活动不存在、奖池已关、次数用完，这些在契约里是<b>返回值</b>
 * （{@code DrawRejectReason}），不是异常。走到本类的只有两种：
 * <ul>
 *   <li>{@link BusinessException} —— 域里还没改造成返回值的那些预期内失败，
 *       翻成 409。⚠️ 这是<b>过渡</b>：见方案 §2.3，域里不该再用它表达预期内结果；</li>
 *   <li>其它 —— 真正的意外，500。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class InternalExceptionHandler {

    /**
     * 域里抛出的业务异常。
     *
     * <p>翻成 409 而不是 500：它至少说明「这次请求本身是可理解的，只是状态不允许」，
     * 让网关能把它和真正的服务端故障分开。但正确的做法仍然是把它改成 reason 返回值 ——
     * 每多一个走到这里的分支，就多一次「用户看到 500 还是 409」取决于实现细节的机会。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<InternalError> handle(BusinessException e, HttpServletRequest request) {
        log.info("[Internal] {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return build(HttpStatus.CONFLICT, "BUSINESS_REJECTED", e.getMessage());
    }

    /** 网关传过来的 body 不合法。这是<b>调用方</b>的问题，不是本服务的，所以 400。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<InternalError> handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("[Internal] {} {} 入参不合法: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", e.getMessage());
    }

    /**
     * 兜底。
     *
     * <p>与 C 端不同：这里<b>把异常原文给出去</b>。调用方是网关不是浏览器，
     * 而网关那一层会把它换成一句固定文案再下发 —— 让排查的人在网关日志里
     * 就能看到下游到底出了什么事，不用两边对时间戳。
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<InternalError> handleUnexpected(Throwable e, HttpServletRequest request) {
        log.error("[Internal] {} {} 未预期异常", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    private static ResponseEntity<InternalError> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new InternalError(code, message, Trace.id()));
    }
}
