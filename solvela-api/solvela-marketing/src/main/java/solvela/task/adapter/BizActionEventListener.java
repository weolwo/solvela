package solvela.task.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import solvela.event.BizActionEvent;
import solvela.member.service.MemberService;
import solvela.task.TaskEvent;
import solvela.task.runtime.TaskEventService;
import solvela.task.runtime.domain.TaskEventReportCommand;
import solvela.task.taskevent.service.TaskEventDefService;

/**
 * 业务动作 → 任务事件的<b>防腐层</b>：全仓唯一同时认识"商城/会员在发生什么"和
 * "任务引擎要什么"的地方。
 *
 * <h3>为什么这个类必须在营销侧，而不是在商城侧调一个接口</h3>
 * 依赖方向。商城、会员、外部场景三个域<b>一个字都不认识任务引擎</b>
 * （{@code PlayBoundaryTest} / {@code ExternalPlayBoundaryTest} 扫字节码常量池守着，
 * member / ledger 更是被 {@code <modules>} 顺序物理挡在编译期之外）。
 * 它们只发一个 {@link BizActionEvent} —— 住在 {@code solvela-model} 的
 * 「平台里发生了一件事」，发布方不知道谁在听。
 *
 * <p>判据：把 {@code solvela-marketing} 从 classpath 上摘掉，
 * {@code solvela-mall} 仍然编译得过、下单仍然跑得通。<b>翻译的成本全部由订阅方承担</b>，
 * 这是防腐层的定义，也是"任务系统不会长进每一个业务里"的全部保证。
 *
 * <h3>🔴 不加 {@code @Async}，尽管那是通用写法</h3>
 * 三条理由，前两条是这个仓库已经付过的学费：
 * <ol>
 *   <li>{@code GlobalEventDispatcher} 的类注释：它实现了 {@code SmartInitializingSingleton}，
 *       {@code @EnableAsync} 默认走 JDK 动态代理，而目标方法不在任何接口上 ——
 *       事件监听器注册时直接报「Need to invoke method ... but not found in any interface(s)
 *       of the exposed proxy type」；</li>
 *   <li>{@code TaskEventService} 的类注释：「异步边界在这里，且<b>刻意不用 @Async 注解</b>」；</li>
 *   <li>{@code @Async} 默认落在 {@code solvela-async-executor} 上 —— 那个池<b>队列无界</b>
 *       （OOM，不是降级）且<b>正被派奖链路占用</b>，塞进去就是拿"进度晚涨"换"积分晚到账"。</li>
 * </ol>
 *
 * <p><b>正确形态</b>：本方法跑在<b>提交线程</b>上，只做翻译 + 调 {@code report()}；
 * 真正的推进在 {@code task-event-executor}（有界 2000 + AbortPolicy）里。
 * <b>异步边界已经在引擎里了，外面不要再套一层。</b>
 *
 * @author alaric
 * @date 2026-09-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BizActionEventListener {

    private final TaskEventService taskEventService;
    private final TaskEventDefService taskEventDefService;
    /** 新老会员的判定归<b>会员域</b>，本类只是调用它 —— 见 {@link #resolveNewMember} */
    private final MemberService memberService;

    /**
     * 接住一件业务动作，翻译成任务事件喂进引擎。
     *
     * <p>🔴 <b>AFTER_COMMIT 是这条链路全部安全性的来源</b>：
     * 主业务事务真正提交之后才打点，所以不存在「订单回滚了但任务进度涨了」；
     * 反过来本方法怎么失败都不会回滚主业务 —— 任务库宕机时用户照样下单。
     *
     * <h3>🔴 为什么开 {@code fallbackExecution}，而 {@code PrizeEventPublisher} 那条链路没开</h3>
     * 默认（不开）的语义是：<b>没有事务时事件直接被丢弃</b> —— 不报错、不打日志。
     * 发奖那条链路认了这个代价，因为在它那里「事务外发奖」本身就是 bug，
     * 宁可不发（那个类的注释写着「表现是奖静默地不发」，是当作已知缺陷记下的）。
     *
     * <p>打点这条链路不一样：这里有<b>合法的无事务生产者</b> ——
     * 签到只写 Redis，一行库都不碰，它没有事务可言。
     * 不开 fallback 的话，唯一的办法是给签到套一个什么都不做的 {@code @Transactional}
     * 来骗出一次 commit，那是让事件机制反过来决定事务边界，本末倒置。
     *
     * <p>开了之后两种情况都对：
     * <ul>
     *   <li><b>有事务</b>：照常等到 AFTER_COMMIT，回滚时不投递 —— 保证一点没变；</li>
     *   <li><b>没事务</b>：当场同步投递，而不是静默消失。</li>
     * </ul>
     *
     * <p>⚠️ 代价说清楚：发布方<b>仍然应当在事务内 publish</b>。
     * 理由从「不然收不到」变成了「不然收得太早」—— 事务外发布会立刻投递，
     * 若业务随后失败回滚，进度却已经涨了。这个理由比原来那个更值得遵守，
     * 因为它的后果是<b>错误的数据</b>，而不只是没数据。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBizAction(BizActionEvent event) {
        try {
            /*
             * 先问注册表。多一次主键点查，换到两件事：
             *
             *   ① 没有任何任务订阅这个动作时，这里【安静地返回】。
             *      不做这一步的话 report() 会抛「未注册或已停用的事件编码」，
             *      而本方法 catch 之后必然打一行 error —— 每下一单打一行。
             *      运营在后台把某个事件「停用」是完全正常的操作，
             *      它不该让日志开始刷错误；
             *
             *   ② 高频动作没人订阅时，连【提交给有界队列】这一步都省了 ——
             *      队列容量是留给真正有人要的事件的。
             *
             * 这次点查跑在提交线程上、事务之外，代价是一次唯一键命中；
             * 相比刚刚提交的那笔下单事务可以忽略。
             */
            TaskEvent def = taskEventDefService.getEnabledByCode(event.actionCode());
            if (def == null) {
                log.debug("【任务打点】没有注册或已停用的动作，忽略。action={}, member={}",
                        event.actionCode(), event.memberId());
                return;
            }

            taskEventService.report(toCommand(event));

        } catch (RuntimeException e) {
            /*
             * 🔴 这里必须把异常全吃掉。
             *
             * 此刻业务事务【已经提交】：订单落了、积分扣了、会员建了。
             * 异常再往上抛，用户收到的是 500，而他会以为下单失败 —— 然后再下一次。
             * 形状与 MallOrderFulfillListener 一致，那边的注释写着同一条理由。
             *
             * 吃掉不等于丢了：队列打满那一种，report() 已经先落了一条
             * t_task_record_flow 的丢弃流水；而「丢了会出事」的那一档
             * （订单支付、充值）由反查对账 job 兜底补推。
             */
            log.error("【任务打点】投递失败，本次进度不涨。action={}, member={}, bizId={}",
                    event.actionCode(), event.memberId(), event.bizId(), e);
        }
    }

    /**
     * 翻译。<b>这是整条链路上唯一一处两个词汇表相遇的地方。</b>
     *
     * <p>今天是 1:1 直传（业务动作码就是任务事件码）。真出现「一个业务动作要喂两个任务事件码」
     * 时，映射写在<b>这里</b>，商城依然一行不动 —— 那才是这个防腐层真正值钱的时候。
     */
    private TaskEventReportCommand toCommand(BizActionEvent event) {
        TaskEventReportCommand cmd = new TaskEventReportCommand();
        cmd.setEventCode(event.actionCode());
        cmd.setMemberId(event.memberId());
        // 幂等键穿透：有天然单号的动作必须带，否则 report() 会当场拒绝并落丢弃流水。
        // 天然没有单号的（签到）传 null，由 TaskPeriodResolver 按事件自然日兜底
        cmd.setEventBizId(event.bizId());
        cmd.setAmount(event.amount());
        // 🔴 事件【实际发生】的时间，不是现在。迟到的事件要归属它发生的那一天，
        //    否则跨零点的那一笔会被算进第二天的周期里
        cmd.setEventTime(event.occurredAt());
        cmd.setIsNewMember(resolveNewMember(event.memberId()));
        cmd.setPayload(event.payload());
        return cmd;
    }

    /**
     * 新老会员由<b>会员域</b>说了算。
     *
     * <h3>为什么内部打点在这里补，而外部上报仍要求上游传</h3>
     * {@code TaskEventReportCommand.isNewMember} 的注释定的规矩是「由上游告知」，
     * 理由是「营销域不拥有会员数据」。那条规矩对<b>外部业务系统</b>成立 ——
     * 它们的会员模型我们不拥有，只能它们自己回答。
     *
     * <p>但对<b>内部打点</b>不成立：让 {@code solvela-mall} 去判断「这人算不算新会员」，
     * 等于把会员域的业务概念搬进商城 —— 正是本方案要消灭的那类耦合。
     * 而 marketing → member 是合法方向（{@code <modules>} 里 member 排在前面），
     * {@code TaskEventService.normalize} 本来就已经按 memberId 查过一次会员表。
     *
     * <p>🔴 判定逻辑在 {@code MemberService.isNewMember}，本方法只是<b>调用</b>它。
     * 别图省事在这里写 {@code createTime.isAfter(now.minusDays(7))} ——
     * 那一刻「新会员」就有了两个定义，而它们会在只改了一边时静默地不一致。
     *
     * <p>查不到会员时如实往下传 {@code null}：配了人群的任务会丢弃事件<b>并写明原因</b>，
     * 那条丢弃流水是运营唯一能看见「上游没告知」的地方。伪造一个 false 会让它变成
     * 一句看起来很正常的「人群不匹配」。
     */
    private Boolean resolveNewMember(Long memberId) {
        try {
            return memberService.isNewMember(memberId);
        } catch (RuntimeException e) {
            // 判不出来不该让整条打点失败：没有人群限制的任务（绝大多数）不受影响
            log.warn("【任务打点】判定新老会员失败，按「未告知」下传。memberId={}", memberId, e);
            return null;
        }
    }
}
