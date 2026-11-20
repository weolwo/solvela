package solvela.consumer.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;
import solvela.anno.PrizeStrategy;
import solvela.consumer.handler.IPrizeHandler;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeLog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 策略注册表的装配规则：注册不上要在启动期红，而不是等到某个奖派发时才报「不支持的奖品类型」。
 */
class PrizeStrategyFactoryTest {

    @Test
    @DisplayName("🔴 handler 被 CGLIB 代理（比如加了 @Transactional）后仍能按注解注册上")
    void 代理后的handler仍能注册() {
        ProxyFactory pf = new ProxyFactory(new ScoreLikeHandler());
        pf.setProxyTargetClass(true);
        IPrizeHandler proxied = (IPrizeHandler) pf.getProxy();
        // 前提：确实是子类代理，类本身上取不到非 @Inherited 的注解 —— 否则这条用例证明不了什么
        assertThat(proxied.getClass().getAnnotation(PrizeStrategy.class)).isNull();

        PrizeStrategyFactory factory = factoryWith(proxied);

        assertThat(factory.getHandler("SCORE")).isSameAs(proxied);
    }

    @Test
    @DisplayName("🔴 同一类型注册两个 handler 启动即失败，不再后者静默覆盖前者")
    void 重复注册启动失败() {
        assertThatThrownBy(() -> factoryWith(new ScoreLikeHandler(), new AnotherScoreHandler()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCORE");
    }

    @Test
    @DisplayName("🔴 实现了 IPrizeHandler 却没挂 @PrizeStrategy 启动即失败，不再静默跳过")
    void 缺注解启动失败() {
        assertThatThrownBy(() -> factoryWith(new UnannotatedHandler()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(UnannotatedHandler.class.getSimpleName());
    }

    private static PrizeStrategyFactory factoryWith(IPrizeHandler... handlers) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        for (int i = 0; i < handlers.length; i++) {
            IPrizeHandler h = handlers[i];
            ctx.registerBean("handler" + i, IPrizeHandler.class, () -> h);
        }
        ctx.refresh();
        PrizeStrategyFactory factory = new PrizeStrategyFactory();
        factory.setApplicationContext(ctx);
        return factory;
    }

    @PrizeStrategy(PrizeTypeEnum.SCORE)
    static class ScoreLikeHandler implements IPrizeHandler {
        @Override
        public DispatchOutcome dispatch(PrizeLog prizeLog) {
            return null;
        }
    }

    @PrizeStrategy(PrizeTypeEnum.SCORE)
    static class AnotherScoreHandler implements IPrizeHandler {
        @Override
        public DispatchOutcome dispatch(PrizeLog prizeLog) {
            return null;
        }
    }

    static class UnannotatedHandler implements IPrizeHandler {
        @Override
        public DispatchOutcome dispatch(PrizeLog prizeLog) {
            return null;
        }
    }
}
