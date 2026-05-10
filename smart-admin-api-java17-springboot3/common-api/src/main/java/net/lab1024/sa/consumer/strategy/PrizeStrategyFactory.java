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
     */
    public IPrizeHandler getHandler(String prizeType) {
        IPrizeHandler handler = strategyMap.get(prizeType);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的奖品类型: " + prizeType);
        }
        return handler;
    }
}