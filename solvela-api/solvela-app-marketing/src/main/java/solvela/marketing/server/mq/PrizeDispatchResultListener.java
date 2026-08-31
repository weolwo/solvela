package solvela.marketing.server.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import solvela.base.mq.MqTopology;
import solvela.base.mqlog.MqMessageLogService;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.member.api.PrizeDispatchResultMessage;
import solvela.prize.prizelog.dao.PrizeLogDao;

import java.nio.charset.StandardCharsets;

/**
 * 收会员服务回来的资产入账结果，落发奖流水的<b>终态</b>。
 *
 * <p>这是发奖被切成两段之后的后半段：
 * <pre>
 *   同步 HTTP：受理 / 拒绝 + 原因 → proposal_status
 *   异步消息：入账成功 / 失败     → status ← 本类
 * </pre>
 *
 * <h3>幂等靠 t_mq_message_log 的唯一索引，不靠本类自己判</h3>
 * 重复投递是消息中间件的常态（网络抖动、消费者重启、死信重投）。
 * 判据是「有没有<b>成功处理过</b>」而不是「有没有见过」—— 失败的必须允许重来，
 * 否则死信重投和后台重试都会被自己的幂等记录挡住。
 *
 * <h3>处理失败时抛出去，让消息进死信</h3>
 * 不吞异常：吞了就等于消息被确认消费，而发奖流水还停在「已受理」——
 * 没有任何地方记得这件事。抛出去至少能进死信队列，运维看得见、能重投。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrizeDispatchResultListener {

    /** 对齐 t_prize_log.fail_reason 的列宽 */
    private static final int FAIL_REASON_MAX_LENGTH = 128;

    private final MqMessageLogService mqMessageLogService;

    private final PrizeLogDao prizeLogDao;

    @RabbitListener(queues = MqTopology.DISPATCH_RESULT_QUEUE)
    public void onDispatchResult(PrizeDispatchResultMessage message, Message raw) {
        String messageId = message.messageId();
        String consumerKey = MqTopology.DISPATCH_RESULT_CONSUMER;

        // 原文入库而不是把对象再序列化一遍：重放时不需要担心"存的和收到的不是同一份"
        String payload = new String(raw.getBody(), StandardCharsets.UTF_8);
        boolean shouldHandle = mqMessageLogService.tryAccept(messageId, consumerKey,
                MqTopology.PRIZE_EXCHANGE, MqTopology.DISPATCH_RESULT_ROUTING_KEY,
                MqTopology.DISPATCH_RESULT_QUEUE, payload);
        if (!shouldHandle) {
            return;
        }

        try {
            int rows = prizeLogDao.updateStatusByExternalBizNo(
                    message.sourceBizId(),
                    message.success() ? PrizeDispatchStatusEnum.SUCCESS : PrizeDispatchStatusEnum.FAIL,
                    SolvelaStringUtil.truncate(message.failReason(), FAIL_REASON_MAX_LENGTH));

            if (rows == 0) {
                // 不是错误：那条 update 只改「仍在等待」的行，人工订正过终态的不该被盖掉。
                // 记成成功，否则这条消息会永远重投
                log.info("【派发结果】发奖流水已是终态，跳过回写。sourceBizId: {}", message.sourceBizId());
            }
            mqMessageLogService.markSuccess(messageId, consumerKey);
        } catch (Exception e) {
            log.error("【派发结果回写失败】sourceBizId: {}", message.sourceBizId(), e);
            mqMessageLogService.markFailed(messageId, consumerKey, e.getMessage());
            // 抛出去 → 进死信队列。吞掉的话消息被确认，而流水还停在「已受理」
            throw e;
        }
    }
}
