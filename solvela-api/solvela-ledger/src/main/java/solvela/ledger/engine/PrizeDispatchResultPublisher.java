package solvela.ledger.engine;

import solvela.member.api.PrizeDispatchResultMessage;

/**
 * 资产入账结果的<b>出口</b>：把「这笔奖到底发出去没有」告诉营销侧。
 *
 * <h3>为什么要这个接口</h3>
 * 同一段入账代码，在两种进程里要做不同的事：
 * <ul>
 *   <li><b>admin</b>（单体，发奖流水就在本进程）—— 直接更新 {@code t_prize_log}；</li>
 *   <li><b>member 服务</b>（发奖流水在营销那边）—— 只能发消息。</li>
 * </ul>
 * 让引擎去判断"我跑在哪个进程里"是最坏的做法。业务只管调这一个方法，
 * <b>由端模块决定装哪个实现</b>（配置项 {@code solvela.prize.dispatch.mode}，
 * 与发布侧的 {@code PrizeEventPublisher} 用的是同一个开关）。
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
