package solvela.base.mq.log;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.MqMessageStatusEnum;
import solvela.mq.MqMessageLog;

import java.time.LocalDateTime;

/**
 * 消息消费的<b>幂等闸门</b>与结果记账。
 *
 * <p>每个消费者开头调一次 {@link #tryAccept}，处理完调 {@link #markSuccess} 或
 * {@link #markFailed}。三个方法覆盖了「重复投递不要重复处理」「失败的要能重来」
 * 「消息到底来没来过」这三件事，各消费者不用再各写一套。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqMessageLogService {

    /** 对齐 t_mq_message_log.fail_reason 的列宽 */
    private static final int FAIL_REASON_MAX_LENGTH = 255;

    private final MqMessageLogDao mqMessageLogDao;

    /**
     * 登记一条消息，并回答<b>「这次要不要处理」</b>。
     *
     * <h3>判据不是「有没有见过」，而是「有没有成功处理过」</h3>
     * 见过但处理失败的，必须允许再来一次 —— 否则死信重投、后台点重试都会被自己的
     * 幂等记录挡住，而那正是失败最需要重来的时候。
     *
     * <p>所以：
     * <ul>
     *   <li>插入成功（第一次见）→ true；</li>
     *   <li>撞唯一键且已成功 → <b>false</b>，跳过。这是重复投递该走的路；</li>
     *   <li>撞唯一键但没成功 → true，重试次数 +1。</li>
     * </ul>
     *
     * <p>🔴 <b>靠数据库唯一索引判重，不靠先查后插</b>：先查后插在并发下两个消费者会同时
     * 查到"没有"，然后双双处理 —— 而消息中间件的重复投递恰恰经常是密集的。
     */
    public boolean tryAccept(String messageId, String consumerKey, String exchange,
                             String routingKey, String queue, String payload) {
        MqMessageLog row = new MqMessageLog();
        row.setMessageId(messageId);
        row.setConsumerKey(consumerKey);
        row.setExchange(exchange);
        row.setRoutingKey(routingKey);
        row.setQueue(queue);
        row.setPayload(payload);
        row.setStatus(MqMessageStatusEnum.RECEIVED);
        row.setRetryCount(0);
        row.setReceiveTime(LocalDateTime.now());
        try {
            mqMessageLogDao.insert(row);
            return true;
        } catch (DuplicateKeyException e) {
            return acceptAgain(messageId, consumerKey);
        }
    }

    /** 已见过：成功过就跳过，没成功过就允许重来并累加重试次数 */
    private boolean acceptAgain(String messageId, String consumerKey) {
        MqMessageLog existing = findOne(messageId, consumerKey);
        if (existing == null) {
            // 撞了唯一键却查不到：只可能是并发插入 + 本次读走了另一个连接的未提交视图，
            // 或者有人手动删了行。放行让它重试一次，比静默跳过安全 —— 消费方本身也要求幂等
            log.warn("【消息幂等】撞唯一键却查不到记录，放行重试。messageId: {}, consumer: {}",
                    messageId, consumerKey);
            return true;
        }
        // 🔴 用 == 比枚举常量，不要 Integer.valueOf(1).equals(status)：
        // 后者对任何类型都编译得过而恒为 false，本仓库已经为这类写法建了棘轮测试
        if (existing.getStatus() == MqMessageStatusEnum.SUCCESS) {
            log.info("【消息幂等】已成功处理过，跳过。messageId: {}, consumer: {}", messageId, consumerKey);
            return false;
        }
        MqMessageLog update = new MqMessageLog();
        update.setId(existing.getId());
        update.setStatus(MqMessageStatusEnum.RECEIVED);
        update.setRetryCount(existing.getRetryCount() == null ? 1 : existing.getRetryCount() + 1);
        mqMessageLogDao.updateById(update);
        return true;
    }

    public void markSuccess(String messageId, String consumerKey) {
        mark(messageId, consumerKey, MqMessageStatusEnum.SUCCESS, null);
    }

    public void markFailed(String messageId, String consumerKey, String failReason) {
        mark(messageId, consumerKey, MqMessageStatusEnum.FAILED, failReason);
    }

    /**
     * ⚠️ 记账失败只打日志，不往外抛：它不能反过来影响业务处理的结果。
     * 一条已经处理成功的消息，不该因为「状态没记上」而被判成失败重来一遍。
     */
    private void mark(String messageId, String consumerKey, MqMessageStatusEnum status, String failReason) {
        try {
            MqMessageLog existing = findOne(messageId, consumerKey);
            if (existing == null) {
                return;
            }
            MqMessageLog update = new MqMessageLog();
            update.setId(existing.getId());
            update.setStatus(status);
            update.setFailReason(SolvelaStringUtil.truncate(failReason, FAIL_REASON_MAX_LENGTH));
            update.setHandleTime(LocalDateTime.now());
            mqMessageLogDao.updateById(update);
        } catch (Exception e) {
            log.error("【消息记账失败】messageId: {}, consumer: {}", messageId, consumerKey, e);
        }
    }

    private MqMessageLog findOne(String messageId, String consumerKey) {
        return mqMessageLogDao.selectOne(Wrappers.<MqMessageLog>lambdaQuery()
                .eq(MqMessageLog::getMessageId, messageId)
                .eq(MqMessageLog::getConsumerKey, consumerKey));
    }
}
