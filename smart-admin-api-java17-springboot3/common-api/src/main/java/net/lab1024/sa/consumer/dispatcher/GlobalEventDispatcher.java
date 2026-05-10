package net.lab1024.sa.consumer.dispatcher;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.EventRoute;
import net.lab1024.sa.consumer.handler.BizEventHandler;
import net.lab1024.sa.domain.event.BaseBizEvent;
import net.lab1024.sa.enums.EventCategoryEnum;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(BaseBizEvent event) {
        RouteTarget target = routeTable.get(event.getCategory());
        if (target == null) {
            log.warn("【事件分发】未找到分类 [{}] 的处理器", event.getCategory());
            return;
        }
        try {
            ReflectionUtils.makeAccessible(target.method);
            target.method.invoke(target.bean, event);
        } catch (Exception e) {
            log.error("【事件分发】处理异常: {}", event.getCategory(), e);
        }
    }
}