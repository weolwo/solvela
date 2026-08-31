package solvela.base.mq;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 的编解码与 template。
 *
 * <h3>⚠️ 2026-08-31：本模块目前【没有任何拓扑声明，也没有任何 publisher / listener】</h3>
 * 它此前唯一的用途是「入账结果从 member 进程回写给 marketing 进程」。
 * 两个进程于 2026-08-31 合并成 solvela-app-biz 之后，那条链路是进程内方法调用了，
 * 于是发奖专用的交换机、队列、死信、绑定共 6 个 bean 与 {@code MqTopology} 里的
 * 一组常量一并删除 —— <b>留一个没人消费的队列比没有队列更危险</b>：
 * 消息静默堆积，或者有人以为它在工作。
 *
 * <p><b>模块本身刻意留着</b>：将来要用 MQ 承载业务事件（如会员登录事件，供多方消费），
 * 那时下面两个 bean 与 {@code MqMessageLog}（消费幂等表，{@code consumer_key}
 * 一列本就是为多消费者设计的）直接就位，只需补拓扑与发布点。
 *
 * <h3>加拓扑时照这个写</h3>
 * 声明写在代码里，不靠人去管理台点 —— Spring AMQP 启动时会把 {@code Exchange} /
 * {@code Queue} / {@code Binding} 类型的 bean 幂等地声明到 broker 上。
 * 靠人在管理台建队列的话，新环境、重装的 broker、CI 全都要有人记得点一遍，
 * 而漏点的表现是「消息发出去了没人收」，不报错。
 *
 * <p>🔴 <b>每个队列都必须绑死信交换机</b>（{@code QueueBuilder.durable(q)
 * .deadLetterExchange(dlx).deadLetterRoutingKey(rk)}）。没有死信配置时，
 * RabbitMQ 对被拒消息的默认行为是<b>直接丢弃</b> —— 消息没了，而且没有任何地方记得。
 * 消费端同时要配 {@code default-requeue-rejected: false}，
 * 否则一条必然失败的消息会被反复重入队，把队列打爆。
 *
 * <p>交换机用 topic：加新事件类型时不用改已有声明。
 *
 * <h3>用 JacksonJsonMessageConverter，不是 Jackson2JsonMessageConverter</h3>
 * 名字里带 2 的那个绑的是 <b>Jackson 2</b>（{@code com.fasterxml.jackson}），
 * 而本项目跟着 Spring Boot 4 走的是 <b>Jackson 3</b>（{@code tools.jackson}）。
 * 用错的表现是启动期 {@code ClassNotFoundException: com.fasterxml.jackson.databind.json.JsonMapper}
 * —— 两个类在同一个包下、只差一个数字，很容易照着老资料抄错。
 *
 * <h3>JSON 而不是 Java 序列化</h3>
 * 发布方与消费方将来会独立发版。Java 序列化要求两边的类完全一致，
 * 加一个字段就可能 InvalidClassException；JSON 加字段是兼容的。
 */
@Configuration
public class MqConfig {

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
     *
     * <p>⚠️ 本仓<b>目前没有 outbox</b>。曾经有过表与实体，但没有任何代码读写，已于
     * 2026-08-31 删除（见 {@code LocalPrizeEventPublisher} 的类注释）。
     * 所以上面那半段是「该有什么」，不是「已经有什么」—— 那个窗口今天是敞着的。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
