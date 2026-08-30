package solvela.dispatch.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import solvela.event.UserPrizeEvent;

/**
 * 进程内派发：发一个 Spring 事件，由本进程的 {@code GlobalEventDispatcher} 在
 * {@code AFTER_COMMIT} 阶段接住。
 *
 * <h3>什么时候用它</h3>
 * <b>admin</b>：它是单体，抽奖与派发在同一个进程、同一个事务边界内，
 * 走 MQ 反而凭空多了一个必须在线的中间件，也让它的验收测试需要一个真 broker。
 *
 * <p>装哪个实现由配置决定：{@code solvela.prize.dispatch.mode}，不填即 {@code local}。
 * <b>业务代码两边完全一样</b>。
 *
 * <p>刻意用配置而不是 {@code @ConditionalOnMissingBean}：后者对 {@code @Component}
 * 的求值依赖扫描顺序，两个实现谁先被看到是不确定的 —— 而"装错了哪个"这件事
 * 在启动日志里毫无提示，直到发现奖没派出去。
 *
 * <h3>它的局限，用之前要清楚</h3>
 * 「事务提交了、进程在派发前挂了」这个窗口它<b>不覆盖</b> —— 事件在内存里，进程没了就没了。
 * 单体形态下这个风险一直存在，只是从来没人为它建过账。真要覆盖，
 * 就该换成 outbox 实现（那张表与是不是走 MQ 无关）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "solvela.prize.dispatch.mode", havingValue = "local", matchIfMissing = true)
public class LocalPrizeEventPublisher implements PrizeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(UserPrizeEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
