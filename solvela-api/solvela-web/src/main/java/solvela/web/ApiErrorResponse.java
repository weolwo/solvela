package solvela.web;

/**
 * 错误响应体。<b>只有失败才有 body 结构，成功直接返回数据本身。</b>
 *
 * <h3>它取代的是什么</h3>
 * 上一版所有响应都套一层 {@code {code, level, ok, msg, data, dataType}}，永远 HTTP 200。
 * 后果是具体的，不是审美问题：
 * <ul>
 *   <li>网关按状态码做的熔断与限流<b>全部失效</b>——所有响应都是 200；</li>
 *   <li>APM 面板上接口成功率永远 100%，故障靠人肉翻日志发现；</li>
 *   <li>前端 HTTP 库无法在拦截器里统一处理 401，只能改成认一个业务码；</li>
 *   <li>重试策略没法写 —— 一个 200 到底该不该重试？</li>
 *   <li>成功时客户端要剥一层才拿到数据，六个字段里 {@code ok} 可由 code 推出、
 *       {@code dataType} 是「这段数据加密没有」的 UI 提示 —— 传输信封里不该有 UI 的概念。</li>
 * </ul>
 *
 * <p>现在：成功 = 2xx + 数据本身（没有内容就是 204），失败 = 4xx/5xx + 本对象。
 * 判断依据回到 HTTP 状态码，这是所有 HTTP 库、网关、监控都认的东西。
 *
 * @param code    稳定的机器码，等于 {@link solvela.code.ErrorCode#name()}。<b>客户端按它分支</b>
 * @param message 给人看的一句话，可直接展示
 * @param traceId 本次请求的链路 id（同时也在 {@code traceId} 响应头上）。
 *                用户截图报障时，凭它一次定位到日志
 */
public record ApiErrorResponse(String code, String message, String traceId) {
}
