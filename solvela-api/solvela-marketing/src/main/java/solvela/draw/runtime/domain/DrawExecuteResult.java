package solvela.draw.runtime.domain;

import solvela.marketing.api.DrawRejectReason;

import java.util.List;

/**
 * 抽奖执行的结果：<b>要么整批没被受理，要么真的抽了 N 次</b>。
 *
 * <h3>为什么是 sealed 而不是一个多字段的 record</h3>
 * 上一版是 {@code DrawExecuteDTO(reject, hit, prizeItemId, prizeCode, source, message)} ——
 * 六个字段编码三种结果，于是 {@code hit=true} 且 {@code reject!=null}、
 * {@code hit=false} 却带着 {@code prizeCode} 这类非法组合<b>全都构造得出来</b>，
 * 规范构造器一个校验都没有。
 *
 * <p>而同一个链路里的 {@code DrawEngine.draw()} 返回的 {@code DrawResult} 早就是
 * sealed + 模式匹配了，{@code DrawExecuteService.execute} 自己就在用 switch 消费它 ——
 * 引擎的返回类型做对了，服务的没有。这里把两者拉齐。
 *
 * <h3>为什么不是 {@code List<单次结果>}</h3>
 * 因为<b>拒绝是整批级的，中没中是单次级的</b>：幂等命中、限流、奖池不存在、配置坏了，
 * 这些一发生就是整批没抽；而 hit / miss 是每一次各自的事。
 * 压成一个列表的话，每条记录都要挂一个恒为 null 的 reject 字段 ——
 * 那就是把刚消掉的非法状态又请回来。
 *
 * <p>调用方用模式匹配消费，加分支时编译器会提醒：
 * <pre>{@code
 * return switch (result) {
 *     case DrawExecuteResult.Rejected(DrawRejectReason reason) -> ...
 *     case DrawExecuteResult.Executed(List<DrawRecord> records) -> ...
 * };
 * }</pre>
 */
public sealed interface DrawExecuteResult {

    /**
     * 整批没被受理：<b>这一批压根没抽</b>，机会与资产都没被消耗，上游该原样退回去。
     *
     * <p>⚠️ 幂等重试<b>不再落到这里</b>。上一版用 {@code DUPLICATE_REQUEST} 这个 reject
     * 表达「你重试了」，而 reject 的契约是「压根没抽、请退还」—— 但第一次其实<b>真的抽了</b>。
     * 于是网络重试会让上游把已经中奖的那一次资产退回去。连抽之后这是 N 份奖的事。
     * 现在幂等命中返回的是<b>第一次的完整结果</b>（{@link Executed}），语义才对得上。
     */
    record Rejected(DrawRejectReason reason) implements DrawExecuteResult {
    }

    /**
     * 真的抽了。{@code records} 的条数等于请求的 {@code times}，顺序即抽奖顺序。
     *
     * <p>每条各自 hit / miss —— 中途某一次扣不动库存不会回滚前面几次
     * （2026-09-01 决定：各抽各的）。扣不动时先降级到兜底奖项，兜底也没有才记 miss。
     */
    record Executed(List<DrawRecord> records) implements DrawExecuteResult {

        public Executed {
            records = List.copyOf(records);
        }

        /** 中奖次数。上游按它算要发几份奖、要不要退还未消耗的机会 */
        public long hitCount() {
            return records.stream().filter(DrawRecord::hit).count();
        }
    }
}
