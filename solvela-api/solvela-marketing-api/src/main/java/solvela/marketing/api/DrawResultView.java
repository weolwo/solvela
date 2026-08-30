package solvela.marketing.api;

/**
 * 抽奖结果。三种情况见 {@link DrawRejectReason} 的类注释。
 *
 * @param reject      没被受理的原因；<b>为 null 表示这一次真的抽了</b>
 * @param hit         是否中奖。{@code reject!=null} 时恒为 false
 * @param prizeCode   中奖奖品编码；未中奖或未受理为 null
 * @param prizeItemId 中奖奖项 id；未中奖或未受理为 null。<b>网关不要原样下发</b> —— 它是奖池内部主键
 * @param source      命中来源 PROBABILITY/WHITE_LIST/FALLBACK_DEGRADE。给排查和风控看，不给用户看
 * @param message     展示文案。未受理时<b>为 null</b> —— 说什么由调用方决定：
 *                    C 端要一句人话，脚本引擎要的是中断，两者对同一个 reason 的处置完全不同
 */
public record DrawResultView(
        DrawRejectReason reject,
        boolean hit,
        String prizeCode,
        Long prizeItemId,
        String source,
        String message) {

    /** 这一次抽奖是否真的执行了（不论中没中）。 */
    public boolean accepted() {
        return reject == null;
    }

    public static DrawResultView ofHit(Long prizeItemId, String prizeCode, String source, String message) {
        return new DrawResultView(null, true, prizeCode, prizeItemId, source, message);
    }

    public static DrawResultView ofMiss(String message) {
        return new DrawResultView(null, false, null, null, null, message);
    }

    public static DrawResultView ofReject(DrawRejectReason reason) {
        return new DrawResultView(reason, false, null, null, null, null);
    }
}
