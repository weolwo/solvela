package solvela.ledger.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;
import solvela.anno.AssetStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.ledger.handler.IAssetHandler;
import solvela.risk.ProposalRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 与 {@code PrizeStrategyFactoryTest} 同一组规则：注册不上要在启动期红，而不是等到入账时才报「不支持的资产类型」。
 */
class AssetStrategyFactoryTest {

    @Test
    @DisplayName("🔴 handler 被 CGLIB 代理（比如加了 @Transactional）后仍能按注解注册上")
    void 代理后的handler仍能注册() {
        ProxyFactory pf = new ProxyFactory(new ScoreLikeHandler());
        pf.setProxyTargetClass(true);
        IAssetHandler proxied = (IAssetHandler) pf.getProxy();
        // 前提：确实是子类代理，类本身上取不到非 @Inherited 的注解 —— 否则这条用例证明不了什么
        assertThat(proxied.getClass().getAnnotation(AssetStrategy.class)).isNull();

        AssetStrategyFactory factory = factoryWith(proxied);

        assertThat(factory.getHandler(PrizeTypeEnum.SCORE)).isSameAs(proxied);
    }

    @Test
    @DisplayName("🔴 同一类型注册两个 handler 启动即失败，不再后者静默覆盖前者")
    void 重复注册启动失败() {
        assertThatThrownBy(() -> factoryWith(new ScoreLikeHandler(), new AnotherScoreHandler()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCORE");
    }

    @Test
    @DisplayName("🔴 实现了 IAssetHandler 却没挂 @AssetStrategy 启动即失败，不再静默跳过")
    void 缺注解启动失败() {
        assertThatThrownBy(() -> factoryWith(new UnannotatedHandler()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(UnannotatedHandler.class.getSimpleName());
    }

    private static AssetStrategyFactory factoryWith(IAssetHandler... handlers) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        for (int i = 0; i < handlers.length; i++) {
            IAssetHandler h = handlers[i];
            ctx.registerBean("handler" + i, IAssetHandler.class, () -> h);
        }
        ctx.refresh();
        AssetStrategyFactory factory = new AssetStrategyFactory();
        factory.setApplicationContext(ctx);
        return factory;
    }

    @AssetStrategy(PrizeTypeEnum.SCORE)
    static class ScoreLikeHandler implements IAssetHandler {
        @Override
        public DispatchOutcome dispatch(ProposalRecord proposal) {
            return null;
        }
    }

    @AssetStrategy(PrizeTypeEnum.SCORE)
    static class AnotherScoreHandler implements IAssetHandler {
        @Override
        public DispatchOutcome dispatch(ProposalRecord proposal) {
            return null;
        }
    }

    static class UnannotatedHandler implements IAssetHandler {
        @Override
        public DispatchOutcome dispatch(ProposalRecord proposal) {
            return null;
        }
    }
}
