package solvela.app.web;

/**
 * 错误响应体。<b>只有失败才有 body 结构，成功直接返回数据本身。</b>
 *
 * <p>上一版是所有响应都套一层 {@code {code, msg, ok, data, level, dataType}}，
 * 成功时客户端要剥一层才拿到数据，六个字段里 {@code ok} 可由 code 推出、
 * {@code dataType} 是 UI 提示 —— 传输信封里不该有 UI 的概念。
 *
 * <p>现在：成功 = 2xx + 数据本身；失败 = 4xx/5xx + 本对象。
 * 客户端的判断依据回到 HTTP 状态码，这是所有 HTTP 库、网关、监控都认的东西。
 *
 * @param code    稳定的机器码，见 {@link ApiErrors#code()}。<b>客户端按它分支</b>
 * @param message 给人看的一句话，可直接展示
 * @param traceId 本次请求的链路 id。用户截图报障时，凭它一次定位到日志
 */
public record ApiErrorResponse(String code, String message, String traceId) {
}
