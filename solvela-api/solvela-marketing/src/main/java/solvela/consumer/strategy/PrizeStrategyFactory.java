package solvela.consumer.strategy;

import lombok.extern.slf4j.Slf4j;
import solvela.anno.PrizeStrategy;
import solvela.consumer.handler.IPrizeHandler;
import solvela.enums.PrizeTypeEnum;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PrizeStrategyFactory implements ApplicationContextAware {

    // 策略注册表
    private final Map<PrizeTypeEnum, IPrizeHandler> strategyMap = new HashMap<>();

    /**
     * 容器启动时，自动扫描并注册所有的策略类。
     *
     * <p>🔴 三种注册不上的情况一律启动失败（{@code PrizeStrategyFactoryTest} 钉住），以前都是静默的，
     * 要等那类奖真被派发时才在 {@code t_prize_log.fail_reason} 里看到「不支持的奖品类型」：
     * <ul>
     *   <li>handler 被 CGLIB 代理（加个 {@code @Transactional} 就会）：{@code @PrizeStrategy} 没有 {@code @Inherited}，
     *       在代理子类上用 {@code getClass().getAnnotation} 取不到，所以必须先拿目标类；</li>
     *   <li>同一类型两个 handler：以前后者静默覆盖前者，哪个生效取决于 bean 遍历顺序；</li>
     *   <li>实现了接口却没挂注解：以前静默跳过。</li>
     * </ul>
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        for (IPrizeHandler handler : applicationContext.getBeansOfType(IPrizeHandler.class).values()) {
            Class<?> targetClass = AopUtils.getTargetClass(handler);
            PrizeStrategy annotation = AnnotationUtils.findAnnotation(targetClass, PrizeStrategy.class);
            if (annotation == null) {
                throw new IllegalStateException("[" + targetClass.getName() + "] 实现了 IPrizeHandler 却没有 @PrizeStrategy，派发时永远路由不到它");
            }
            IPrizeHandler existed = strategyMap.putIfAbsent(annotation.value(), handler);
            if (existed != null) {
                throw new IllegalStateException("奖品类型 " + annotation.value() + " 注册了两个派发策略: "
                        + AopUtils.getTargetClass(existed).getName() + " 与 " + targetClass.getName());
            }
            log.info("【策略工厂注册】加载奖品派发策略: [{}] -> {}", annotation.value(), targetClass.getSimpleName());
        }
    }

    /**
     * 获取策略路由。
     *
     * <p>入参以前是 String，要先 valueOf 成枚举再查 —— 直接拿 String 查枚举键的 map 编译通过但恒为 null，
     * 表现为「所有奖品类型都不支持」（GlobalEventDispatcher 与两个策略工厂都踩过）。
     * 2026-11-21 prize_type / asset_type 整条链路改成 PrizeTypeEnum 后，这个坑在类型上就不存在了。
     */
    public IPrizeHandler getHandler(PrizeTypeEnum prizeType) {
        IPrizeHandler handler = prizeType == null ? null : strategyMap.get(prizeType);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的奖品类型: " + prizeType
                    + "，已注册策略: " + strategyMap.keySet());
        }
        return handler;
    }
}