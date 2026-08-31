package solvela.ledger.engine;

import solvela.member.api.PrizeDispatchResultMessage;

/**
 * 资产入账结果的<b>出口</b>：把「这笔奖到底发出去没有」告诉营销侧。
 *
 * <h3>为什么要这个接口</h3>
 * 入账与发奖流水<b>不一定在同一个进程里</b>。在同一个进程时直接更新
 * {@code t_prize_log}；不在时只能发消息。让引擎去判断"我跑在哪个进程里"
 * 是最坏的做法 —— 业务只管调这一个方法，<b>由端模块决定装哪个实现</b>。
 *
 * <p>⚠️ 2026-08-31：今天三个进程里发奖流水与入账都在同一进程，所以只有
 * {@link LocalPrizeDispatchResultPublisher} 一个实现，MQ 那个已删除。
 *
 * <p><b>接口刻意留着</b>：目标形态是把资产域独立成服务（会员 + 资产一个独立控制台），
 * 那一天入账会重新跑在另一个进程里，回写就必须变回发消息。到时候加一个实现、
 * 在端模块里换装配即可，{@code AssetDispatcher} 那条链路一行都不用动。
 */
public interface PrizeDispatchResultPublisher {

    /**
     * 交付一个入账结果。
     *
     * <p>🔴 <b>实现必须自己吞异常</b>：回写失败不能影响已经完成的入账。
     * 「因为消息发不出去而把已经到账的钱回滚」比状态不一致糟得多。
     */
    void publish(PrizeDispatchResultMessage message);
}
