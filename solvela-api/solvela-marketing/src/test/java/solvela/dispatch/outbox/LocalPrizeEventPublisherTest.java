package solvela.dispatch.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import solvela.event.UserPrizeEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 钉住 {@link PrizeEventPublisher#publish} 的前提：必须在事务内。
 *
 * <p>事务外 publish 的事件不会触发 {@code AFTER_COMMIT} 监听器 —— 奖静默不发。
 * 以前这条前提只写在注释里，这里让它变成「违反即抛」。
 */
class LocalPrizeEventPublisherTest {

    private final ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);

    private final LocalPrizeEventPublisher publisher = new LocalPrizeEventPublisher(springPublisher);

    private final UserPrizeEvent event = UserPrizeEvent.builder()
            .sourceBizId("T-1")
            .prizeCode("P1")
            .build();

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("🔴 事务外 publish 直接抛，不让奖静默丢掉")
    void 事务外抛异常() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> publisher.publish(event));

        assertThat(e.getMessage()).contains("T-1").contains("事务");
        verifyNoInteractions(springPublisher);
    }

    @Test
    @DisplayName("只有同步注册、没有真实事务（如 SUPPORTS 传播）同样拒绝 —— AFTER_COMMIT 监听器两者都要")
    void 只有同步没有事务也抛() {
        TransactionSynchronizationManager.initSynchronization();

        assertThrows(IllegalStateException.class, () -> publisher.publish(event));
        verify(springPublisher, never()).publishEvent(event);
    }

    @Test
    @DisplayName("事务内照常发出 Spring 事件")
    void 事务内正常发布() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publish(event);

        verify(springPublisher).publishEvent(event);
    }
}
