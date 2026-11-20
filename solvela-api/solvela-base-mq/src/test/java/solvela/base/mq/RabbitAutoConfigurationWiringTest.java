package solvela.base.mq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.rabbitmq.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 经典 RabbitMQ 的自动配置真的装上了，而且读的是 {@code spring.rabbitmq.*}。
 *
 * <h3>🔴 为什么要有这条</h3>
 * Spring Boot 4.2 把 4.1 的 {@code spring-boot-starter-amqp} 拆成了三个名字很像的 starter（见本模块 pom 的注释）。
 * 升级时第一次选成了 {@code spring-boot-starter-amqp-rabbitmq} —— 那是 AMQP 1.0 客户端，配置前缀是
 * {@code spring.amqp.rabbitmq}。它间接带着 RabbitTemplate 这些类，所以<b>编译、单测全绿</b>，
 * 而经典的 Rabbit 自动配置根本不在 classpath 上：app-biz 启动时拿不到 ConnectionFactory，
 * yaml 里的 {@code spring.rabbitmq.host} 也没人读。
 *
 * <p>这里不连 broker：CachingConnectionFactory 是懒连接，只验「装配 + 属性绑定」这一层。
 */
@DisplayName("RabbitMQ 自动配置装配")
class RabbitAutoConfigurationWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(MqConfig.class);

    @Test
    @DisplayName("🔴 spring.rabbitmq.host / port 绑定到连接工厂（前缀错了这里会是默认的 localhost:5672）")
    void 读的是spring_rabbitmq前缀() {
        runner.withPropertyValues("spring.rabbitmq.host=mq.solvela.internal", "spring.rabbitmq.port=5673")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(ConnectionFactory.class);
                    CachingConnectionFactory cf = ctx.getBean(CachingConnectionFactory.class);
                    assertThat(cf.getHost()).isEqualTo("mq.solvela.internal");
                    assertThat(cf.getPort()).isEqualTo(5673);
                });
    }

    @Test
    @DisplayName("RabbitTemplate 用的是 MqConfig 那个，带 Jackson 3 的消息转换器")
    void 模板来自MqConfig() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(RabbitTemplate.class);
            assertThat(ctx.getBean(RabbitTemplate.class).getMessageConverter())
                    .isInstanceOf(JacksonJsonMessageConverter.class);
        });
    }
}
