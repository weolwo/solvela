package net.lab1024.sa.ledger.strategy;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.AssetStrategy;
import net.lab1024.sa.enums.PrizeTypeEnum;
import net.lab1024.sa.ledger.handler.IAssetHandler;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AssetStrategyFactory implements ApplicationContextAware {

    // 策略注册表
    private final Map<PrizeTypeEnum, IAssetHandler> strategyMap = new HashMap<>();

    /**
     * 容器启动时，自动扫描并注册所有的策略类
     */
    @Override
    public void setApplicationContext (ApplicationContext applicationContext) throws BeansException {
        // 获取所有实现了 IPrizeHandler 接口的 Bean
        Map<String, IAssetHandler> beans = applicationContext.getBeansOfType(IAssetHandler.class);

        for (IAssetHandler handler : beans.values()) {
            // 提取类上的注解
            AssetStrategy annotation = handler.getClass().getAnnotation(AssetStrategy.class);
            if (annotation != null) {
                strategyMap.put(annotation.value(), handler);
                log.info("【策略工厂注册】加载奖品派发策略: [{}] -> {}", annotation.value(), handler.getClass().getSimpleName());
            }
        }
    }

    /**
     * 获取策略路由
     */
    public IAssetHandler getHandler(String prizeType) {
        IAssetHandler handler = strategyMap.get(prizeType);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的奖品类型: " + prizeType);
        }
        return handler;
    }
}