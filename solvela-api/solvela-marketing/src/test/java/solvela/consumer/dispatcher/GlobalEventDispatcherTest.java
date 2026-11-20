package solvela.consumer.dispatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import solvela.anno.EventRoute;
import solvela.base.config.AsyncConfig;
import solvela.enums.EventCategoryEnum;
import solvela.event.BaseBizEvent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 路由表的装配规则：同一分类挂两个处理器，以前是后注册的静默覆盖前者 ——
 * 哪个生效取决于 bean 的遍历顺序，表现是「某类事件有时由 A 处理、换个版本变成 B」。
 */
class GlobalEventDispatcherTest {

    @Test
    @DisplayName("🔴 同一分类两个路由启动即失败，报出两边是谁")
    void 重复路由启动失败() {
        // SmartInitializingSingleton 里抛的异常 refresh() 原样往外抛，不包一层 BeanCreationException
        assertThatThrownBy(() -> start(TaskRouteA.class, TaskRouteB.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TASK")
                .hasMessageContaining("TaskRouteA")
                .hasMessageContaining("TaskRouteB");
    }

    @Test
    @DisplayName("不同分类各一个路由照常启动 —— 确认上一条红的是重复，不是装配本身")
    void 不同分类正常启动() {
        assertThatCode(() -> start(TaskRouteA.class, PrizeRoute.class).close()).doesNotThrowAnyException();
    }

    private static AnnotationConfigApplicationContext start(Class<?>... routes) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(AsyncConfig.ASYNC_EXECUTOR_THREAD_NAME, AsyncTaskExecutor.class,
                () -> new SimpleAsyncTaskExecutor());
        ctx.register(GlobalEventDispatcher.class);
        ctx.register(routes);
        ctx.refresh();
        return ctx;
    }

    static class TaskRouteA {
        @EventRoute(EventCategoryEnum.TASK)
        public void on(BaseBizEvent event) {
        }
    }

    static class TaskRouteB {
        @EventRoute(EventCategoryEnum.TASK)
        public void on(BaseBizEvent event) {
        }
    }

    static class PrizeRoute {
        @EventRoute(EventCategoryEnum.PRIZE)
        public void on(BaseBizEvent event) {
        }
    }
}
