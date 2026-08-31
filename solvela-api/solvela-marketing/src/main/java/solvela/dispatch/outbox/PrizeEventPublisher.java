package solvela.dispatch.outbox;

import solvela.event.UserPrizeEvent;

/**
 * 中奖之后「把奖交出去」的唯一出口。
 *
 * <h3>为什么要这个接口</h3>
 * 三个发布点（抽奖、任务、彩票）唯一的共同出口 —— 将来要加投递保证、
 * 加埋点、加限流，只有一个地方要改；业务代码也不必判断"我跑在哪个进程里"。
 *
 * <h3>今天只有一个实现</h3>
 * {@link LocalPrizeEventPublisher}，<b>admin 与营销服务装的是同一个</b>。
 * 两个进程的发奖路径完全一样：写本地 {@code t_prize_log}，再调
 * {@code MemberProposalApi} 建提案。差别只在 {@code MemberProposalApi} 解析成谁 ——
 * admin 是进程内的 {@code ProposalApiService}，营销服务是
 * {@code MemberServiceClientConfig} 造的 HTTP 代理。
 *
 * <p>⚠️ 2026-08-31 更正：这里原先写着「营销服务必须写 outbox 并投递到 MQ」——
 * <b>那个实现从来没有落地</b>，配套的实体与 Dao 已随本次一并删除（它们除了互相引用零使用）。
 * 真要补投递保证时加一个实现即可，业务代码不用动 —— 那正是留着这个接口的意义。
 */
public interface PrizeEventPublisher {

    /**
     * 交付一个中奖事件。
     *
     * <p>🔴 <b>必须在业务事务内调用</b>，理由比看上去严重得多：
     * 接住这个事件的 {@code GlobalEventDispatcher.dispatch} 挂在
     * {@code @TransactionalEventListener(AFTER_COMMIT)} 上，而它<b>没有</b>开
     * {@code fallbackExecution}。在事务外 publish 的事件根本不会触发监听器 ——
     * 表现是<b>奖静默地不发</b>：没有异常、没有日志、流水停在原地。
     *
     * <p>本方法<b>不保证</b>派发已经完成，只保证「事务提交之后会派发」。
     * 「提交了、进程在派发前挂了」这个窗口它不覆盖，见 {@link LocalPrizeEventPublisher}。
     */
    void publish(UserPrizeEvent event);
}
