package solvela.dispatch;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发奖投递 outbox：<b>MQ 负责投递，本表负责不丢</b>。
 *
 * <p>拆成四个服务之后，抽奖在营销服务、派发在会员服务，中间靠 RabbitMQ。
 * 而 MQ 覆盖不了这个窗口：<b>事务提交成功了，进程在发消息之前挂了</b> ——
 * 奖已判定、流水已落库，消息却没发出去，而且没有任何地方记得这件事。
 * publisher-confirm 也救不了它：确认回调的前提是消息真的发出去了。
 *
 * <p>所以发消息之前先在<b>同一个事务里</b>写一行，提交后再投递；
 * 投递成功标记完成，失败或进程挂掉则由重投任务扫出来重发。
 *
 * <p>⚠️ 消费方必须幂等 —— 重投必然带来重复投递。
 */
@Data
@TableName("t_prize_dispatch_outbox")
public class PrizeDispatchOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源单号：与 {@code t_prize_log.external_biz_no} 同值，消费方据它幂等。唯一索引 */
    private String sourceBizId;

    /** 路由键，见 {@code MqTopology} */
    private String routingKey;

    /**
     * 事件 JSON 原文。
     *
     * <p>存原文而不是存字段：重投时不需要再拼一次，<b>也就不可能拼得跟当初不一样</b>。
     */
    private String payload;

    /** 0-待投递, 1-已投递 */
    private Integer status;

    /** 重投次数。持续增长说明下游有问题，是最直接的告警指标 */
    private Integer retryCount;

    /** 最后一次失败原因 */
    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
