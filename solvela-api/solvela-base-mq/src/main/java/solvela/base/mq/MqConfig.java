package solvela.base.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 的拓扑声明与编解码。
 *
 * <h3>声明写在代码里，不靠人去管理台点</h3>
 * Spring AMQP 启动时会把这些 bean 声明到 broker 上（幂等）。
 * 靠人在管理台建队列的话，新环境、重装的 broker、CI 全都要有人记得点一遍 ——
 * 而漏点的表现是「消息发出去了没人收」，不报错。
 *
 * <h3>用 JacksonJsonMessageConverter，不是 Jackson2JsonMessageConverter</h3>
 * 名字里带 2 的那个绑的是 <b>Jackson 2</b>（{@code com.fasterxml.jackson}），
 * 而本项目跟着 Spring Boot 4 走的是 <b>Jackson 3</b>（{@code tools.jackson}）。
 * 用错的表现是启动期 {@code ClassNotFoundException: com.fasterxml.jackson.databind.json.JsonMapper}
 * —— 两个类在同一个包下、只差一个数字，很容易照着老资料抄错。
 *
 * <h3>JSON 而不是 Java 序列化</h3>
 * 两侧是两个进程、将来会独立发版。Java 序列化要求两边的类完全一致，
 * 加一个字段就可能 InvalidClassException；JSON 加字段是兼容的。
 */
@Configuration
public class MqConfig {

    @Bean
    public TopicExchange prizeExchange() {
        return new TopicExchange(MqTopology.PRIZE_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange prizeDlxExchange() {
        return new TopicExchange(MqTopology.DLX_EXCHANGE, true, false);
    }

    /**
     * 资产入账结果的回写队列。<b>绑了死信交换机</b> ——
     * 消费方 reject 且不重入队时，消息进死信而不是被丢掉。
     */
    @Bean
    public Queue dispatchResultQueue() {
        return QueueBuilder.durable(MqTopology.DISPATCH_RESULT_QUEUE)
                .deadLetterExchange(MqTopology.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqTopology.DISPATCH_RESULT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue dispatchResultDlq() {
        return QueueBuilder.durable(MqTopology.DISPATCH_RESULT_DLQ).build();
    }

    @Bean
    public Binding dispatchResultBinding() {
        return BindingBuilder.bind(dispatchResultQueue())
                .to(prizeExchange()).with(MqTopology.DISPATCH_RESULT_ROUTING_KEY);
    }

    @Bean
    public Binding dispatchResultDlqBinding() {
        return BindingBuilder.bind(dispatchResultDlq())
                .to(prizeDlxExchange()).with(MqTopology.DISPATCH_RESULT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * ⚠️ 这里只配编解码，<b>不配 publisher-confirm 的回调</b>。
     *
     * <p>确认回调能告诉你「broker 收没收到」，但它救不了「事务提交了、进程在发消息前挂了」——
     * 那个窗口只有 outbox 能覆盖。两者不是替代关系：
     * <b>MQ 负责投递，outbox 负责不丢</b>，各管一段。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
