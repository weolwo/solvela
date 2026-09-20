package solvela.base.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import solvela.event.BizActionEvent;

/**
 * 进程内投递：发一个 Spring 事件，由本进程的监听器在 {@code AFTER_COMMIT} 阶段接住。
 *
 * <p>{@link BizEventPublisher} <b>今天唯一的实现</b>。admin 与 app-biz 装的都是它 ——
 * 两个进程的打点路径因此完全一样。
 *
 * <h3>它的局限，用之前要清楚</h3>
 * 「业务事务提交了、进程在投递前挂了」这个窗口它<b>不覆盖</b> ——
 * 事件在内存里，进程没了就没了。业务已经是既成事实，却没有任何地方记得还欠着一次投递。
 *
 * <p>这个窗口<b>不靠这个类兜</b>，也不靠 MQ 兜（publisher-confirm 只能告诉你 broker
 * 收没收到，救不了这一段）。它由<b>反查对账 job</b> 兜，而且只兜"丢了会出事"的那一档：
 * 订单支付、充值。签到、浏览这类丢了就丢了，用户再点一次即可。
 *
 * <p>这么选的前提是<b>重推必须安全</b>，而它已经成立：
 * {@code t_task_record_flow} 上有唯一键 {@code uk_t_tsk_flw_evt}
 * （task_config_id + member_id + event_biz_id），{@code TaskEventService} 在 catch
 * {@code DuplicateKeyException}。换任何异步传输最贵的那一关就是这个，它已经过了。
 *
 * @author alaric
 * @date 2026-09-17
 */
@Component
@RequiredArgsConstructor
public class LocalBizEventPublisher implements BizEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(BizActionEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
