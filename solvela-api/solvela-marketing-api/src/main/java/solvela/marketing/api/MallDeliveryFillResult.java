package solvela.marketing.api;

/**
 * 「用地址簿的地址补填履约单」的结果。
 *
 * <h3>为什么不直接复用 member-api 的 {@code DeliveryFillResult}</h3>
 * 因为 {@code solvela-marketing-api} <b>不依赖</b> {@code solvela-member-api}
 * —— 两个契约模块各自只依赖 {@code solvela-contract}，谁也不认识谁。
 * 那不是疏忽：它们对齐的是<b>将来的两个服务</b>，一旦互相引用，
 * 「拆开」这件事就从改配置变成了改代码。
 *
 * <p>所以这里是一层薄转换，由 {@code MallClientFacade} 把资产域的结果翻过来。
 * 多一个 record 的代价，换的是两个契约之间没有边。
 *
 * @param accepted 填上了没有
 * @param message  给用户看的一句话，<b>原样透传资产域给的措辞</b> ——
 *                 商城这一层不重新拼，不然同一件事会有两种说法
 * @author alaric
 * @date 2026-09-18
 */
public record MallDeliveryFillResult(boolean accepted, String message) {
}
