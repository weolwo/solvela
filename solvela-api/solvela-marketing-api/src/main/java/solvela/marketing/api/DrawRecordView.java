package solvela.marketing.api;

/**
 * <b>一次</b>抽奖的结果。N 连抽就有 N 条，顺序即抽奖顺序。
 *
 * <p>这里<b>没有 reject</b>：受理与否是整批级的事，见 {@link DrawResultView}。
 * 每条记录只回答一件事 —— 这一次中没中。
 *
 * @param hit         是否中奖
 * @param prizeCode   中奖奖品编码；未中奖为 null
 * @param prizeItemId 中奖奖项 id；未中奖为 null。<b>网关不要原样下发</b> —— 它是奖池内部主键
 * @param source      命中来源 PROBABILITY/WHITE_LIST/FALLBACK_DEGRADE。
 *                    给排查和风控看，<b>不给用户看</b>：让用户知道自己是白名单命中的，
 *                    等于告诉他这个活动内定了
 */
public record DrawRecordView(boolean hit, String prizeCode, Long prizeItemId, String source) {
}
