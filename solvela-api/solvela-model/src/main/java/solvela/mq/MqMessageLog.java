package solvela.mq;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.MqMessageStatusEnum;

import java.time.LocalDateTime;

/**
 * 消息接收记录：<b>唯一索引即消费幂等</b>，只保留 7 天。
 *
 * <h3>它同时是三样东西</h3>
 * <ol>
 *   <li><b>幂等闸门</b>：{@code (message_id, consumer_key)} 唯一，重复投递在插入那一刻被挡住，
 *       不用每个消费者各写一套去重；</li>
 *   <li><b>重试依据</b>：处理失败的行留在库里，后台可以按 {@code consumer_key} 挑出来重跑；</li>
 *   <li><b>排查证据</b>：「消息到底发过来没有」不用去翻两个服务的日志对时间戳。</li>
 * </ol>
 *
 * <h3>🔴 为什么隔离列叫 consumer_key 而不是 activity_code</h3>
 * 这张表要装两类消息：
 * <ul>
 *   <li><b>活动事件</b>（会员登录、下单…）：一条消息被多个活动消费，
 *       {@code consumer_key} 填活动编码 —— 后台重试 A 活动时不会碰到 B；</li>
 *   <li><b>发奖回写</b>：消费它的是一个固定 handler，填 handler 名。</li>
 * </ul>
 * 叫 activity_code 的话，后一类只能填 null，而"null 是什么意思"很快会有两种解释。
 *
 * <p>⚠️ 唯一键<b>必须</b>含 consumer_key。只按 message_id 唯一的话，
 * 同一条消息的第二个消费者插不进去，会被误判成「重复投递」而静默跳过 ——
 * 表现是「一个活动收到了、另一个没收到」，最难查的那一类。
 */
@Data
@TableName("t_mq_message_log")
public class MqMessageLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息唯一标识，发送方生成 */
    private String messageId;

    private String exchange;

    /** 路由键。将来活动挂事件监听就是按它路由的 */
    private String routingKey;

    /** 消费者标识：活动事件填活动编码，固定消费者填 handler 名 */
    private String consumerKey;

    /** 队列名。定位用，不参与唯一键 —— 需要隔离的是消费者，不是队列 */
    private String queue;

    /** 消息 JSON 原文。存原文而不是解析后的字段 —— 重放时不需要再拼一次 */
    private String payload;

    /** 处理状态。幂等的判据是 SUCCESS，不是「有没有这行」—— 见 {@link MqMessageStatusEnum} */
    private MqMessageStatusEnum status;

    private String failReason;

    /** 重试次数。持续增长是最直接的告警指标 */
    private Integer retryCount;

    private LocalDateTime receiveTime;

    private LocalDateTime handleTime;
}
