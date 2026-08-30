package solvela.member.server.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import solvela.base.mq.MqTopology;
import solvela.ledger.engine.PrizeDispatchResultPublisher;
import solvela.member.api.PrizeDispatchResultMessage;

/**
 * 把资产入账结果发给营销服务。
 *
 * <p>装配条件是 {@code solvela.prize.dispatch.mode=mq}，只有会员服务这么配。
 * admin 是单体，用 {@code LocalPrizeDispatchResultPublisher} 直接改库。
 *
 * <h3>🔴 发送失败只记日志，绝不往外抛</h3>
 * 走到这里时<b>钱已经到账了</b>。为一次消息发送失败去回滚已完成的入账，
 * 比状态暂时不一致糟得多。
 *
 * <p>那这条消息就丢了吗 —— 是的，所以营销侧留着
 * {@code proposal_status=ACCEPTED 但 status 迟迟不落终态} 的行可以被扫出来重对。
 * <b>消息中间件解决投递，解决不了"发送方根本没发出去"</b>，这一段的兜底在对账，不在 MQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "solvela.prize.dispatch.mode", havingValue = "mq")
public class MqPrizeDispatchResultPublisher implements PrizeDispatchResultPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(PrizeDispatchResultMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    MqTopology.PRIZE_EXCHANGE, MqTopology.DISPATCH_RESULT_ROUTING_KEY, message);
            log.info("【派发结果已投递】sourceBizId: {}, success: {}",
                    message.sourceBizId(), message.success());
        } catch (Exception e) {
            log.error("【派发结果投递失败】sourceBizId: {}, 营销侧的发奖流水将停在「已受理」，"
                    + "需由对账任务补齐", message.sourceBizId(), e);
        }
    }
}
