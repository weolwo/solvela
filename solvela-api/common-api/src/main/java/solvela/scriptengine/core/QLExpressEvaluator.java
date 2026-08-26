package solvela.scriptengine.core;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.exception.QLException;
import com.alibaba.qlexpress4.exception.QLRuntimeException;
import com.alibaba.qlexpress4.exception.QLSyntaxException;
import com.alibaba.qlexpress4.exception.QLTimeoutException;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import lombok.extern.slf4j.Slf4j;
import solvela.exception.BusinessException;
import solvela.exception.EngineScriptException;
import solvela.scriptengine.domain.EngineFunctionMeta;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.domain.ScriptSource;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptEvaluator;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 QLExpress 4.x 的防腐层实现
 */
@Slf4j
public class QLExpressEvaluator implements ScriptEvaluator {

    /**
     * 执行上下文在 QL attachments 里的 key。
     *
     * <p>4.x 的 AST 递归传参是无状态的，函数拿上下文走 {@code QContext.attachment()} 即可，
     * <b>不需要也不允许用 ThreadLocal</b> —— 那是 3.x 时代在 Tomcat 下漏内存的老路。
     */
    static final String ATTACH_ENGINE_CONTEXT = "__sa_engine_context__";

    private final Express4Runner runner;

    private final ScriptEngineProperties properties;

    public QLExpressEvaluator(ScriptEngineProperties properties) {
        this.properties = properties;
        // 默认走隔离策略：脚本碰不到任何未经 @ScriptFunction 白名单暴露的 Java 成员
        QLSecurityStrategy securityStrategy = properties.isAllowJavaReflect()
                ? QLSecurityStrategy.open()
                : QLSecurityStrategy.isolation();
        this.runner = new Express4Runner(InitOptions.builder()
                .securityStrategy(securityStrategy)
                .build());
        log.info("[ScriptEngine] QLExpress 引擎初始化完成，安全策略={}, 超时={}ms, 精确小数={}",
                properties.isAllowJavaReflect() ? "open(危险)" : "isolation",
                properties.getTimeoutMillis(), properties.isPrecise());
    }

    @Override
    public String name() {
        return "QLExpress-4.x";
    }

    @Override
    public void check(ExecutableScript script) throws EngineScriptException {
        try {
            // check 只走语法树，不产生编译缓存，可以安全地对任意内容调用
            runner.check(script.content());
        } catch (QLSyntaxException e) {
            throw new EngineScriptException("[" + script.name() + "] 脚本语法错误", describe(e));
        }
    }

    @Override
    public Object evaluate(ExecutableScript script, EngineContext engineContext) {
        // 必须命中 execute(String, Map, QLOptions) 这个重载。
        // execute(String, Object, QLOptions) 是把对象当「属性根」用的，
        // 传 EngineContext 进去的话 bind() 的变量在脚本里一个都取不到。
        //
        // 🔴 只喂 getScriptVariables()：内部数据通道（traceId / 操作人 / 幂等键等）
        //    绝不能流进脚本可见的变量表。整个 EngineContext 走 attachments 给 Java 函数用。
        Map<String, Object> variables = new HashMap<>(engineContext.getScriptVariables());
        try {
            return runner.execute(script.content(), variables, buildOptions(script, engineContext)).getResult();
        } catch (QLTimeoutException e) {
            throw new EngineScriptException("[" + script.name() + "] 脚本执行超时", describe(e));
        } catch (QLException e) {
            // 脚本调用的 Java 函数抛出的业务异常，要原样还给调用方 —— 那是业务语义（比如「积分不足」），
            // 不是脚本错误。QL 会把它包进 QLRuntimeException 的 catchObj 里，这里拆出来。
            BusinessException businessException = unwrapBusinessException(e);
            if (businessException != null) {
                throw businessException;
            }
            // 剩下的才是真正的脚本问题：翻译成带行列号的领域异常，交给上层决定怎么呈现
            throw new EngineScriptException("[" + script.name() + "] 脚本执行失败", describe(e));
        }
    }

    /**
     * 从 QL 异常里把业务异常捞出来。
     *
     * <p>两条路径都要看：{@code QLRuntimeException.getCatchObj()} 存的是被捕获的原始对象，
     * 而某些包装路径只把它挂在 cause 链上。
     */
    private BusinessException unwrapBusinessException(QLException e) {
        if (e instanceof QLRuntimeException runtimeException
                && runtimeException.getCatchObj() instanceof BusinessException businessException) {
            return businessException;
        }
        for (Throwable cause = e.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof BusinessException businessException) {
                return businessException;
            }
        }
        return null;
    }

    @Override
    public void registerFunction(EngineFunctionMeta meta) {
        // 用自研适配器而不是 QMethodFunction：需要支持 EngineContext 自动注入，
        // 并把 InvocationTargetException 拆开，让业务异常原样往上抛
        boolean added = runner.addFunction(meta.getFunctionName(), new QLExpressFunctionAdapter(meta));
        if (!added) {
            throw new IllegalStateException("脚本函数 [" + meta.getFunctionName() + "] 注册失败，引擎中已存在同名函数");
        }
    }

    @Override
    public void clearCompileCache() {
        runner.clearCompileCache();
        log.info("[ScriptEngine] 编译缓存已清空");
    }

    private QLOptions buildOptions(ExecutableScript script, EngineContext engineContext) {
        Map<String, Object> attachments = new HashMap<>(2);
        attachments.put(ATTACH_ENGINE_CONTEXT, engineContext);
        return QLOptions.builder()
                .timeoutMillis(properties.getTimeoutMillis())
                .precise(properties.isPrecise())
                // 脚本内部定义的变量不回写宿主上下文，跑完即销毁
                .polluteUserContext(false)
                .cache(isCacheable(script))
                .attachments(attachments)
                .build();
    }

    /**
     * 由「脚本来源」这个业务事实推导出「本引擎要不要缓存编译产物」这个实现决策。
     *
     * <p>这个映射是 <b>QLExpress 独有的</b>，所以它住在这里而不是 {@code ExecutableScript} 上：
     * QL 的编译缓存以脚本原文为 key 且无容量上限，UNTRUSTED 内容的 key 基数无界，
     * 进去就再也出不来。换成别的引擎，同一个 {@link ScriptSource} 完全可以推导出别的结论。
     */
    private boolean isCacheable(ExecutableScript script) {
        return script.source() == ScriptSource.TRUSTED;
    }

    /**
     * 把 QL 异常整理成人能读的一行，前端编辑器可以直接拿去划红线
     */
    private String describe(QLException e) {
        StringBuilder detail = new StringBuilder();
        if (e.getLineNo() > 0) {
            detail.append("第 ").append(e.getLineNo()).append(" 行");
            if (e.getColNo() > 0) {
                detail.append(" 第 ").append(e.getColNo()).append(" 列");
            }
            detail.append("：");
        }
        detail.append(e.getReason() != null ? e.getReason() : e.getMessage());
        if (e.getErrLexeme() != null && !e.getErrLexeme().isBlank()) {
            detail.append(" (出错位置: ").append(e.getErrLexeme()).append(")");
        }
        return detail.toString();
    }
}
