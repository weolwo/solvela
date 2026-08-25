package solvela.consumer.dispatcher;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.anno.EventRoute;
import solvela.base.config.AsyncConfig;
import solvela.consumer.handler.BizEventHandler;
import solvela.domain.event.BaseBizEvent;
import solvela.enums.EventCategoryEnum;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class GlobalEventDispatcher implements SmartInitializingSingleton {

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 派发用线程池：复用 AsyncConfig 里已有的那个，不另起炉灶
     */
    @Resource(name = AsyncConfig.ASYNC_EXECUTOR_THREAD_NAME)
    private AsyncTaskExecutor asyncExecutor;

    // 路由表封装了目标 Bean 和调用的 Method
    private final Map<EventCategoryEnum, RouteTarget> routeTable = new HashMap<>();

    private static class RouteTarget {
        Object bean;
        Method method;
        RouteTarget(Object bean, Method method) { this.bean = bean; this.method = method; }
    }

    @Override
    public void afterSingletonsInstantiated() {
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);

            // ==========================================
            // 模式 1：类级别路由 (重型业务)
            // ==========================================
            EventRoute classRoute = AnnotationUtils.findAnnotation(targetClass, EventRoute.class);
            if (classRoute != null) {
                if (!(bean instanceof BizEventHandler<?>)) {
                    throw new RuntimeException("类级别路由 [" + targetClass.getName() + "] 必须实现 BizEventHandler 接口!");
                }
                try {
                    // 获取接口的 handle 方法
                    Method handleMethod = targetClass.getMethod("handle", BaseBizEvent.class);
                    routeTable.put(classRoute.value(), new RouteTarget(bean, handleMethod));
                    log.info("【事件路由】加载 [类级别] 分类: {} -> {}", classRoute.value(), targetClass.getSimpleName());
                } catch (NoSuchMethodException e) {
                    log.error("路由加载失败", e);
                }
                continue; // 如果是类级别，就不再扫描它的方法了
            }

            // ==========================================
            // 模式 2：方法级别路由 (轻量级业务聚拢)
            // ==========================================
            ReflectionUtils.doWithMethods(targetClass, method -> {
                EventRoute methodRoute = AnnotationUtils.findAnnotation(method, EventRoute.class);
                if (methodRoute != null) {
                    routeTable.put(methodRoute.value(), new RouteTarget(bean, method));
                    log.info("【事件路由】加载 [方法级别] 分类: {} -> {}#{}", methodRoute.value(), targetClass.getSimpleName(), method.getName());
                }
            });
        }
    }

    /**
     * 事务提交后派发：路由解析同步做，真正的处理器调用丢给线程池。
     *
     * 压测实测：派发同步执行时抽奖 QPS 从 241 掉到 94（p50 384ms -> 891ms），
     * 而派发在 AFTER_COMMIT 阶段执行、成败都不影响已提交的主事务，天然适合挪出主线程。
     *
     * 这里刻意不用 @Async：本类实现了 SmartInitializingSingleton，@EnableAsync 默认走 JDK 动态代理，
     * 而 dispatch 不在任何接口上，事件监听器注册时会直接报
     * 「Need to invoke method 'dispatch' ... but not found in any interface(s) of the exposed proxy type」。
     * 显式提交线程池既绕开了代理语义，也让「未找到处理器」这类路由错误仍留在请求线程里，最容易被发现。
     *
     * 注意：处理器一旦进池，异常就不再沿调用栈上抛，只能靠下面的 catch 落日志 + 下游表自查来发现问题。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(BaseBizEvent event) {
        // BaseBizEvent.category 是 String，而路由表以 EventCategoryEnum 为键。
        // Map.get(Object) 不做类型检查，直接拿 String 去查枚举键的 map 编译能过但恒为 null，
        // 曾导致整条派发链路静默失效（事件照发、下游一条不落），故此处必须显式转成枚举再查。
        EventCategoryEnum category = resolveCategory(event.getCategory());
        RouteTarget target = category == null ? null : routeTable.get(category);
        if (target == null) {
            log.warn("【事件分发】未找到分类 [{}] 的处理器", event.getCategory());
            return;
        }
        asyncExecutor.execute(() -> invokeHandler(target, event));
    }

    private void invokeHandler(RouteTarget target, BaseBizEvent event) {
        try {
            ReflectionUtils.makeAccessible(target.method);
            target.method.invoke(target.bean, event);
        } catch (Exception e) {
            log.error("【事件分发】处理异常: {}", event.getCategory(), e);
        }
    }

    /**
     * 事件里的分类是字符串，转成路由表的枚举键；非法值只告警不抛，避免一个脏事件打断监听器
     */
    private EventCategoryEnum resolveCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return EventCategoryEnum.valueOf(category);
        } catch (IllegalArgumentException e) {
            log.warn("【事件分发】无法识别的事件分类: {}", category);
            return null;
        }
    }
}