package net.lab1024.sa.consumer.strategy;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.PrizeStrategy;
import net.lab1024.sa.consumer.handler.IPrizeHandler;
import net.lab1024.sa.enums.PrizeTypeEnum;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PrizeStrategyFactory implements ApplicationContextAware {

    // 策略注册表
    private final Map<PrizeTypeEnum, IPrizeHandler> strategyMap = new HashMap<>();

    /**
     * 容器启动时，自动扫描并注册所有的策略类
     */
    @Override
    public void setApplicationContext (ApplicationContext applicationContext) throws BeansException {
        // 获取所有实现了 IPrizeHandler 接口的 Bean
        Map<String, IPrizeHandler> beans = applicationContext.getBeansOfType(IPrizeHandler.class);

        for (IPrizeHandler handler : beans.values()) {
            // 提取类上的注解
            PrizeStrategy annotation = handler.getClass().getAnnotation(PrizeStrategy.class);
            if (annotation != null) {
                strategyMap.put(annotation.value(), handler);
                log.info("【策略工厂注册】加载奖品派发策略: [{}] -> {}", annotation.value(), handler.getClass().getSimpleName());
            }
        }
    }

    /**
     * 获取策略路由
     *
     * 注意：strategyMap 以 PrizeTypeEnum 为键，而入参是字符串。
     * Map.get(Object) 不做类型检查，直接拿 String 去查会编译通过但恒为 null，
     * 表现为「所有奖品都不支持」。与 GlobalEventDispatcher 曾踩过的是同一个坑，必须先转枚举。
     */
    public IPrizeHandler getHandler(String prizeType) {
        PrizeTypeEnum type = resolveType(prizeType);
        IPrizeHandler handler = type == null ? null : strategyMap.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的奖品类型: " + prizeType
                    + "，已注册策略: " + strategyMap.keySet());
        }
        return handler;
    }

    private PrizeTypeEnum resolveType(String prizeType) {
        if (prizeType == null || prizeType.isBlank()) {
            return null;
        }
        try {
            return PrizeTypeEnum.valueOf(prizeType);
        } catch (IllegalArgumentException e) {
            log.warn("【策略工厂】无法识别的奖品类型: {}", prizeType);
            return null;
        }
    }
}