package solvela.app.domain;

/**
 * C 端看到的抽奖结果。
 *
 * <h3>刻意<b>不</b>下发的两个字段</h3>
 * <ul>
 *   <li>{@code prizeItemId} —— 奖池内部主键。下发它等于把配置结构暴露给客户端；</li>
 *   <li>{@code source} —— 命中来源（概率/白名单/兜底降级）。这是给排查和风控看的：
 *       让用户知道自己是「白名单命中」的，等于告诉他这个活动内定了。</li>
 * </ul>
 *
 * <p>「没被受理」不在这里 —— 它由 HTTP 状态码表达（4xx），不是一个 body 字段。
 * 能拿到本对象就说明这一次真的抽了。
 *
 * @param hit       是否中奖
 * @param prizeCode 中奖奖品编码；未中奖为 null
 * @param message   展示文案
 */
public record DrawView(boolean hit, String prizeCode, String message) {
}
