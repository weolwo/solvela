package solvela.marketing.api;

import java.util.List;

/**
 * 一批抽奖的结果：<b>要么整批没被受理，要么真的抽了 N 次</b>。
 *
 * <h3>为什么拒绝在这一层、中没中在下一层</h3>
 * 受理与否是<b>整批级</b>的（幂等、限流、活动没开、奖池不存在）—— 一发生就是这一批压根没抽；
 * 而 hit / miss 是<b>每一次各自</b>的事。三种情况：
 * <ul>
 *   <li><b>没被受理</b>：{@code reject != null}，{@code records} 为空。
 *       这一批压根没抽，机会与资产都没被消耗，上游该原样退回去；</li>
 *   <li><b>抽了但没中</b>：{@code reject == null}，某条记录 {@code hit=false}。
 *       这是<b>正常结果</b>，抽奖发生了；</li>
 *   <li><b>中了</b>：某条记录 {@code hit=true}。</li>
 * </ul>
 * 第一条与后两条的区别决定<b>要不要退还机会</b>，后两条之间的区别决定<b>要不要发奖</b>。
 * 混在一起表达（比如都用 message 字符串）时，这两个决定就只能靠猜。
 *
 * <h3>为什么不是 sealed</h3>
 * 引擎内部那个 {@code DrawExecuteResult} 是 sealed 的，这里刻意不是 ——
 * 本类是<b>跨进程契约</b>（{@code @HttpExchange}），sealed 接口没有类型标签就反序列化不回来。
 * 代价是「reject 非空却带着记录」这种状态在类型上仍然构造得出来，
 * 所以由规范构造器兜住：两者必须互斥。
 *
 * <h3>🔴 这里没有 message</h3>
 * 措辞是<b>调用方</b>的决定。同一个「没中奖」，C 端要说「手慢了」，内部工具要看到的是事实本身；
 * 同一个 {@code POOL_CLOSED}，C 端要说「活动暂时休息一下」。域只陈述发生了什么。
 *
 * @param reject  没被受理的原因；<b>为 null 表示这一批真的抽了</b>
 * @param records 每一次的结果，顺序即抽奖顺序；未受理时为空列表
 */
public record DrawResultView(DrawRejectReason reject, List<DrawRecordView> records) {

    public DrawResultView {
        records = records == null ? List.of() : List.copyOf(records);
        if (reject != null && !records.isEmpty()) {
            throw new IllegalArgumentException("没被受理就不该有抽奖记录: " + reject);
        }
        if (reject == null && records.isEmpty()) {
            throw new IllegalArgumentException("受理了却一条记录都没有 —— 抽 0 次是非法输入，应当以 INVALID_TIMES 拒绝");
        }
    }

    /** 这一批抽奖是否真的执行了（不论中没中）。 */
    public boolean accepted() {
        return reject == null;
    }

    /** 实际抽了几次。未受理为 0 */
    public int times() {
        return records.size();
    }

    /** 中了几次。上游按它算要发几份奖 */
    public long hitCount() {
        return records.stream().filter(DrawRecordView::hit).count();
    }

    public static DrawResultView ofReject(DrawRejectReason reason) {
        return new DrawResultView(reason, List.of());
    }

    public static DrawResultView ofExecuted(List<DrawRecordView> records) {
        return new DrawResultView(null, records);
    }
}
