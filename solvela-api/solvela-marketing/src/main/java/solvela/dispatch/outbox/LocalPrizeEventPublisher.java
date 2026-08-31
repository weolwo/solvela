package solvela.dispatch.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import solvela.event.UserPrizeEvent;

/**
 * 进程内派发：发一个 Spring 事件，由本进程的 {@code GlobalEventDispatcher} 在
 * {@code AFTER_COMMIT} 阶段接住。
 *
 * <p>{@link PrizeEventPublisher} <b>今天唯一的实现</b> —— admin 与营销服务装的都是它。
 * 两个进程的发奖路径因此完全一样，差别只在下游 {@code MemberProposalApi}
 * 是进程内实现还是 HTTP 代理。
 *
 * <h3>它的局限，用之前要清楚</h3>
 * 「事务提交了、进程在派发前挂了」这个窗口它<b>不覆盖</b> —— 事件在内存里，进程没了就没了。
 * 奖已判定、流水已落库，却没有任何地方记得还欠着一次派发。
 *
 * <p>⚠️ <b>2026-08-31：别以为投递保证已经有了 —— 它没有。</b>
 * 此前仓里有一张 {@code t_prize_dispatch_outbox} 表、一个实体和一个 Dao，
 * 但<b>没有任何代码读写它们</b>（除了互相引用零使用），已删除。
 * 真要覆盖那个窗口，就补一个 outbox 实现：业务事务里写一行、提交后再投递、
 * 失败或进程挂掉由重投任务扫出来。业务代码一行都不用改。
 *
 * <h3>为什么不再有 @ConditionalOnProperty</h3>
 * 本类此前挂着 {@code @ConditionalOnProperty(name = "solvela.prize.dispatch.mode",
 * havingValue = "local", matchIfMissing = true)}，注释说「装哪个实现由配置决定」。
 * 但<b>另一个实现从来没存在过</b>，所以那个条件只可能减掉本 bean、不可能选中别的:
 * 谁按当时的注释给营销服务配上 {@code dispatch.mode=mq}，得到的是
 * {@code DrawExecuteService} 构造注入失败、<b>整个营销服务起不来</b>。
 *
 * <p>那个配置键本身仍然在用，但它管的是<b>另一件事</b>：ledger 的
 * {@code PrizeDispatchResultPublisher}（入账结果回写），那个是真有 local / mq 两套实现的，
 * 会员服务配的 {@code mq} 指的是它。两件不相干的事共用一个键，本身就该拆开。
 *
 * <p>将来真加了第二个实现，再把条件加回来 —— 到那时它才有意义。
 */
@Component
@RequiredArgsConstructor
public class LocalPrizeEventPublisher implements PrizeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(UserPrizeEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
