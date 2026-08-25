package solvela.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import solvela.scriptengine.core.DefaultScriptEngine;
import solvela.scriptengine.core.QLExpressEvaluator;
import solvela.scriptengine.core.ScriptEngineProperties;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptEvaluator;

/**
 * 脚本引擎装配中心
 */
@Configuration
@EnableConfigurationProperties(ScriptEngineProperties.class)
public class ScriptEngineAutoConfiguration {

    // -------------------------------------------------------------
    // 引擎插槽：按 script.engine 配置决定实例化哪个底座
    // -------------------------------------------------------------

    @Bean
    @ConditionalOnProperty(prefix = "script", name = "engine", havingValue = "qlexpress", matchIfMissing = true)
    public ScriptEvaluator qlExpressEvaluator(ScriptEngineProperties properties) {
        return new QLExpressEvaluator(properties);
    }

    /* 未来扩展：
    @Bean
    @ConditionalOnProperty(prefix = "script", name = "engine", havingValue = "aviator")
    public ScriptEvaluator aviatorEvaluator(ScriptEngineProperties properties) {
        return new AviatorEvaluator(properties);
    }
    */

    // -------------------------------------------------------------
    // 门面装配：业务方只注入 ScriptEngine，不需要 @Qualifier
    // -------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public ScriptEngine scriptEngine(ScriptEvaluator evaluator) {
        return new DefaultScriptEngine(evaluator);
    }
}
