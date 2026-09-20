package solvela.base.event;

import solvela.event.BizActionEvent;

/**
 * 业务动作事件的<b>唯一出口</b>。
 *
 * <h3>为什么要这个接口，而不是让业务直接注入 {@code ApplicationEventPublisher}</h3>
 * 它把"将来换传输层"的改动面积框在一个地方。直接用 Spring 的发布器，
 * 那就是散落在 N 个 service 里的 N 个调用点，换的那天要一个个找出来重写，
 * 而它们不会有任何标记 —— 这正是 {@code MallBoundaryTest} 失败信息里警告过的那种形状。
 *
 * <h3>今天只有一个实现：{@link LocalBizEventPublisher}（进程内 Spring 事件）</h3>
 * <p>⚠️ <b>换 MQ 不是"加一个实现"那么轻。</b>照实说清楚它是什么：
 * <ul>
 *   <li>不能只是把 {@code publishEvent} 换成 {@code convertAndSend} ——
 *       那是<b>双写</b>：消息发出去了、业务事务回滚了，下游按一件没发生的事推进度；</li>
 *   <li>真正的 MQ 实现 = outbox 表（业务事务里写一行）+ relay（提交后投递）
 *       + 重投 job + 交换机/队列拓扑 + 死信。<b>那是一个迭代，不是一个类</b>；</li>
 *   <li>被换掉的只有<b>调用点不变</b>这一条：消费侧（监听器）要新写，
 *       投递语义会从"恰好一次"变成"至少一次"。</li>
 * </ul>
 *
 * <p>🔴 <b>不要在这里挂 {@code @ConditionalOnProperty}。</b>
 * {@code LocalPrizeEventPublisher} 此前挂过
 * {@code @ConditionalOnProperty(name = "...mode", havingValue = "local", matchIfMissing = true)}，
 * 注释说「装哪个实现由配置决定」—— 但<b>另一个实现从来没存在过</b>，
 * 所以那个条件只可能减掉本 bean、不可能选中别的：谁按注释配了 {@code mode=mq}，
 * 得到的是构造注入失败、<b>整个服务起不来</b>。
 * 等第二个实现真的存在了再把条件加回来，到那时它才有意义。
 *
 * <h3>什么时候才该有第二个实现</h3>
 * 触发条件写死在方案 §6.5，免得将来靠感觉决定：
 * <ul>
 *   <li><b>outbox</b>：反查对账 job 的分钟级延迟被产品判定为不可接受；</li>
 *   <li><b>真 MQ</b>：同一批事件出现<b>多个独立消费方</b>，
 *       或资产域拆出去导致发布方与消费方不在一个进程。</li>
 * </ul>
 * outbox 是 MQ 的<b>前置条件</b>而不是替代品 —— 先做 outbox，
 * 将来把"job 轮询投递"换成"relay 投 broker"，发布方和消费方都不动。
 *
 * @author alaric
 * @date 2026-09-17
 */
public interface BizEventPublisher {

    /**
     * 广播一件已经发生的事。
     *
     * <p>🔴 <b>有事务的业务，必须在事务内调用。</b>接住它的监听器挂在
     * {@code @TransactionalEventListener(AFTER_COMMIT)} 上：事务提交后才投递，
     * 回滚时不投递 —— 所以「单没落成但进度涨了」在这个形状下不可能发生。
     *
     * <p>⚠️ 监听器开了 {@code fallbackExecution}，所以事务外 publish <b>不会被丢弃</b>，
     * 而是<b>当场同步投递</b>。这是为签到那种只写 Redis、压根没有事务的生产者留的口子
     * （不开的话就只能给它套一个什么都不做的 {@code @Transactional} 来骗出一次 commit）。
     *
     * <p>但这不是"随便在哪发都行"：在一个<b>有事务的</b>业务里把 publish 写到事务外，
     * 后果是<b>投递得太早</b> —— 业务随后回滚，进度却已经涨了。
     * 那比收不到更糟：收不到是没数据，这是错数据。
     *
     * <p>本方法<b>不保证</b>下游已经处理完，只保证「事务提交之后会投递」。
     * 「提交了、进程在投递前挂了」这个窗口它不覆盖 —— 那个窗口今天由
     * <b>反查对账 job</b> 兜（只对丢了会出事的那一档，见方案 §6.3/§6.4），
     * 而不是由这个接口兜。
     */
    void publish(BizActionEvent event);
}
