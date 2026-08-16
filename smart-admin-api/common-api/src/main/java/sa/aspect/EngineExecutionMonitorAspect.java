package sa.aspect;

import lombok.extern.slf4j.Slf4j;
import sa.base.common.exception.BusinessException;
import sa.base.common.exception.EngineScriptException;
import sa.base.common.util.JsonUtils;
import sa.scriptengine.domain.ExecutableScript;
import sa.scriptengine.spi.EngineContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 🚀 规则引擎全局监控与异常兜底切面 (SRE 的终极防线)
 */
@Slf4j
@Aspect
@Component
public class EngineExecutionMonitorAspect {

    // 🌟 1. 精准狙击：拦截 ScriptEngine 门面接口下的所有 evaluate 方法！
    @Pointcut("execution(* sa.base.module.support.scriptengine.core.*.evaluate(..))")
    public void evaluatePointcut() {
    }

    // 🌟 2. 环绕通知：将执行过程死死包裹在我们的监控中
    @Around("evaluatePointcut()")
    public Object monitorAndRescue(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 尝试从拦截的方法参数中，扒出"犯罪现场"的证据（脚本和上下文）
        String scriptName = null;
        ExecutableScript executableScript = null;
        EngineContext context = null;
        Object[] args = pjp.getArgs();
        if (args.length >= 2) {
            executableScript = (ExecutableScript) args[0];
            if (args[1] instanceof EngineContext) {
                context = (EngineContext) args[1];
            }
        }
        if (Objects.nonNull(executableScript)) {
            scriptName = executableScript.getName();
        }

        try {
            // 放行！让底层的 QLExpress 去执行
            Object result = pjp.proceed();

            // 耗时监控 (大厂标准的 Slow Log)
            long costTime = System.currentTimeMillis() - startTime;
            log.warn("🐢 [ScriptEngine] 规则执行 耗时: {}ms, 脚本: {}", costTime, scriptName);

            return result;

        } catch (Throwable e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("💥 [RuleEngine 核心引擎崩溃] 执行耗时: {}ms", costTime);
            log.error("👉 崩溃脚本: {},参数：{}", scriptName, JsonUtils.toJson(context));

            // 🚨 拦截我们刚刚翻译的“领域专属异常”
            if (e instanceof EngineScriptException rse) {
                log.error("👉 脚本精准报错: {},{}", rse.getScriptErrorDetail(), scriptName);

                // 把精准的错误细节（比如"缺少分号"）直接拼接好抛给前端！前端代码编辑器就可以直接弹窗提示了！
                throw new BusinessException(rse.getMessage() + " 详细原因: " + rse.getScriptErrorDetail());
            }
            // 拦截普通的业务异常
            else if (e instanceof BusinessException) {
                throw e;
            }
            // 最后的未知异常兜底
            else {
                log.error("👉 堆栈异常:", e);
                throw new BusinessException("系统动态规则执行遇到未知异常，请联系管理员！");
            }
        }
    }
}