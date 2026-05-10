package net.lab1024.sa.config;

import net.lab1024.sa.scriptengine.core.DefaultScriptEngine;
import net.lab1024.sa.scriptengine.spi.QLExpressEvaluator;
import net.lab1024.sa.scriptengine.spi.ScriptEngine;
import net.lab1024.sa.scriptengine.spi.ScriptEvaluator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 🚀 规则引擎自动化装配中心 (真正的 Starter 级设计)
 */
@Configuration
public class ScriptEngineAutoConfiguration {

    // -------------------------------------------------------------
    // 🔌 插件插槽区：根据 application.yml 的配置，按需实例化底座
    // -------------------------------------------------------------

    @Bean
    // 只有当 yaml 里配置了 qlexpress，或者压根没配（默认兜底）时，才实例化 QL 引擎！
    @ConditionalOnProperty(prefix = "script", name = "engine", havingValue = "qlexpress", matchIfMissing = true)
    public ScriptEvaluator qlExpressEvaluator() {
        return new QLExpressEvaluator();
    }

    /* //  未来的无缝扩展：
    @Bean
    @ConditionalOnProperty(prefix = "smart.rule", name = "active-engine", havingValue = "aviator")
    public ScriptEvaluator aviatorEvaluator() {
        return new AviatorEvaluator();
    }
    */

    // -------------------------------------------------------------
    // 👑 门面装配区：彻底干掉 @Qualifier！
    // -------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public ScriptEngine defaultRuleEngine(ScriptEvaluator evaluator) {
        // 这里的 evaluator 参数，Spring 会自动把上面那个成功激活的 Bean 传进来！
        // 彻底告别 @Qualifier 强绑定，优雅到极致！
        return new DefaultScriptEngine(evaluator);
    }
}