package solvela.member.api;

/**
 * 领取结果。
 *
 * <h3>🔴 为什么不是直接返回 {@code String}</h3>
 * 第一版就是返回裸 {@code String}，结果是<b>跨进程那一步直接炸</b>：
 * app-biz 把它序列化成 {@code text/plain}，而网关的 {@code RestClient}
 * 没有 {@code text/plain → String} 的转换器 ——
 * <pre>
 *   UnknownContentTypeException: no suitable HttpMessageConverter found for
 *   response type [class java.lang.String] and content type [text/plain]
 * </pre>
 * 单进程内调用完全正常，只有真的跨进程才暴露。
 *
 * <p>⚠️ 修法不是「给网关加一个转换器」：裸 {@code String} 本来就是个长不大的契约 ——
 * 哪天要多告诉端上一件事（比如「你还有 2 份没领」），加字段会<b>破坏所有调用方</b>，
 * 而 record 加一个字段是兼容的。
 *
 * @param assetName 领到的东西叫什么，如「生日 20 元券」。端上直接拿去提示
 *
 * @author alaric
 * @date 2026-09-22
 */
public record EntitlementClaimResult(String assetName) {
}
