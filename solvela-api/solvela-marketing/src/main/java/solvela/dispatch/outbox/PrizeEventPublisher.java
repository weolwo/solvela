package solvela.dispatch.outbox;

import solvela.event.UserPrizeEvent;

/**
 * 中奖之后「把奖交出去」的唯一出口。
 *
 * <h3>为什么要这个接口</h3>
 * 同一段抽奖/任务/彩票代码，在<b>两种进程</b>里要做不同的事：
 * <ul>
 *   <li><b>admin</b>（单体，装着派发链路本身）—— 发一个进程内 Spring 事件就够了；</li>
 *   <li><b>marketing 服务</b>（派发在会员服务那侧）—— 必须写 outbox 并投递到 MQ。</li>
 * </ul>
 * 让业务代码去判断"我跑在哪个进程里"是最坏的做法：那等于把部署形态写进了业务逻辑。
 * 所以业务只管调这一个方法，<b>由端模块决定装哪个实现</b>。
 *
 * <p>这也是三个发布点（抽奖、任务、彩票）唯一的共同出口 —— 将来要加投递保证、
 * 加埋点、加限流，只有一个地方要改。
 */
public interface PrizeEventPublisher {

    /**
     * 交付一个中奖事件。
     *
     * <p>🔴 <b>必须在业务事务内调用</b>：outbox 实现要靠这一点把「写 outbox」
     * 和「写发奖流水」放进同一个事务，否则那张表就失去了意义。
     *
     * <p>本方法<b>不保证</b>派发已经完成，只保证「这件事不会丢」。
     */
    void publish(UserPrizeEvent event);
}
