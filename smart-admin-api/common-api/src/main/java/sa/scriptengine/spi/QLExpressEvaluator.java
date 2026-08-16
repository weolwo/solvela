package sa.scriptengine.spi;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.exception.QLException;
import com.alibaba.qlexpress4.exception.QLSyntaxException;
import com.alibaba.qlexpress4.runtime.function.QMethodFunction;
import lombok.extern.slf4j.Slf4j;
import sa.base.common.exception.EngineScriptException;
import sa.scriptengine.domain.EngineFunctionMeta;
import sa.scriptengine.domain.ExecutableScript;

import java.util.Map;

/**
 * 基于 QLExpress 4.1.0 (全新 Antlr4 引擎) 的防腐层实现
 */
@Slf4j
public class QLExpressEvaluator implements ScriptEvaluator {

    private final Express4Runner runner;

    public QLExpressEvaluator() {
        // 4.x 启用了全新的 InitOptions 配置项，自带极致优化
        this.runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);
        log.info("成功初始化 QLExpress 4.1.0 引擎底座!");
    }

    @Override
    public String name() {
        return "QLExpress-4.1.0";
    }

    @Override
    public boolean checkScript(ExecutableScript executableScript) throws Exception {
        try {
            runner.parseToDefinitionWithCache(executableScript.getContent());
        } catch (QLSyntaxException e) {
            throw new EngineScriptException(e.getMessage(), e.toString());
        }
        return true;
    }

    @Override
    public Object evaluate(ExecutableScript script, EngineContext engineContext) {
        try {
            EngineContextHolder.set(engineContext);
            return runner.execute(script.getContent(), engineContext, QLOptions.DEFAULT_OPTIONS).getResult();
        } catch (Exception e) {
            // 🌟 核心魔法：异常翻译！
            // 如果是 QL 抛出的语法异常/运行异常，提取它精准的报错信息（比如：第2行缺少 ')'）
            if (e instanceof QLException || e.getClass().getName().contains("QLException")) {
                String detailMessage = e.getMessage(); // 拿到 QL 原始的精准报错

                // 翻译成咱们系统的领域异常，向上抛出！
                throw new EngineScriptException("[" + script.getName() + "] 脚本语法或运行错误，请检查！", detailMessage);
            }
            // 其他未知异常，原样抛出让上层兜底
            throw e;
        }finally {
            EngineContextHolder.clear();
        }
    }

    // 在 QLExpressEvaluator.java 中：
    @Override
    public void registerFunction(EngineFunctionMeta meta) {
        // 完美的面向对象封装，将我们的 Meta 模型包装成 QL 4.x 认识的 CustomFunction
        runner.addFunction(meta.getFunctionName(), new QMethodFunction(meta.getTargetBean(), meta.getMethod()));
    }
}