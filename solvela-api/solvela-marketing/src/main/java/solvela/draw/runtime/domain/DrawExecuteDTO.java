package solvela.draw.runtime.domain;

import solvela.marketing.api.DrawRejectReason;

/**
 * 执行抽奖 结果（record：只序列化输出，不需要框架反射 set）
 *
 * <h3>三种结果，别混</h3>
 * 中奖（hit）/ 没中奖（miss）/ 没被受理（reject）。第三种表示这一次<b>压根没抽</b>，
 * 机会与资产都没被消耗，上游该原样退回去。详见 {@link DrawRejectReason} 的类注释。
 *
 * <p>{@code reject} 是 2026-08-30 加的：在那之前这几种情况由引擎抛
 * {@code BusinessException}，而抽奖有两个调用方，它们对同一件事的处置<b>本来就不同</b>：
 * <ul>
 *   <li>脚本引擎在编排里调用 —— 被拒时该<b>中断整段脚本</b>，异常是天然的机制；</li>
 *   <li>C 端接口直接 Java 调用 —— 被拒时要给用户一句人话和一个 4xx，异常会变成 500。</li>
 * </ul>
 * 所以引擎只陈述事实（返回 reason），<b>把「抛不抛」留给调用方</b>。
 * 这与会员认证的 {@code MemberAuthResult} 是同一个形状。
 *
 * <p>⚠️ 后台联调接口 {@code /drawPrizeLog/execute} 的行为跟着变了：以前被拒是一个错误响应，
 * 现在是 200 加一个 {@code reject} 字段。造数与压测脚本判定成功与否时要看 {@code reject}。
 *
 * @param reject      未受理原因；为 null 表示这一次真的抽了
 * @param hit         是否中奖
 * @param prizeItemId 中奖奖项ID（未中奖为 null）
 * @param prizeCode   中奖奖品编码（未中奖为 null）
 * @param source      命中来源：PROBABILITY/WHITE_LIST/FALLBACK_DEGRADE
 * @param message     展示文案；未受理时为 null（说什么由调用方定）
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawExecuteDTO(DrawRejectReason reject, boolean hit, Long prizeItemId, String prizeCode,
                             String source, String message) {

    public boolean accepted() {
        return reject == null;
    }

    public static DrawExecuteDTO ofHit(long prizeItemId, String prizeCode, String source) {
        return new DrawExecuteDTO(null, true, prizeItemId, prizeCode, source, "恭喜中奖");
    }

    public static DrawExecuteDTO ofMiss(String message) {
        return new DrawExecuteDTO(null, false, null, null, null, message);
    }

    public static DrawExecuteDTO ofReject(DrawRejectReason reason) {
        return new DrawExecuteDTO(reason, false, null, null, null, null);
    }
}
