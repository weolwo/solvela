package solvela.scriptengine.core;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.data.convert.ParametersTypeConvertor;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import solvela.exception.BusinessException;
import solvela.scriptengine.domain.EngineFunctionMeta;
import solvela.scriptengine.spi.EngineContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 把一个 {@code @ScriptFunction} 标注的 Java 方法适配成 QLExpress 的自定义函数。
 *
 * <p>没有直接用 QL 自带的 {@code QMethodFunction}，因为要多做两件事：
 * <ol>
 *   <li>方法首参声明为 {@code EngineContext} 时，从 {@code QContext.attachment()} 自动注入 ——
 *       这样 Java 函数能拿到执行上下文，而不用退回 ThreadLocal；</li>
 *   <li>拆掉 {@link InvocationTargetException}，让业务方法里抛的 BusinessException
 *       原样冒到上层，而不是被包成一层反射异常；</li>
 *   <li>对标了 {@code sideEffect = true} 的函数执行「一次执行只准调一次」的硬约束，见
 *       {@link #SIDE_EFFECT_GUARD_KEY}。</li>
 * </ol>
 */
public class QLExpressFunctionAdapter implements CustomFunction {

    /**
     * 副作用哨兵在<b>内部数据通道</b>里的 key，值是第一次调用的那个函数名。
     *
     * <p>放内部通道而不是脚本变量通道：脚本<b>看不见也改不掉</b>它。
     * 放脚本变量里等于把这道约束的开关交给被约束的人。
     *
     * <p>双下划线前缀是给人看的 —— 万一哪天有人 dump 内部通道，一眼知道这不是业务数据。
     */
    public static final String SIDE_EFFECT_GUARD_KEY = "__sideEffectCalled";

    private final EngineFunctionMeta meta;

    private final Method method;

    /**
     * 脚本侧真正需要传的形参类型（已剔除被注入的 EngineContext）
     */
    private final Class<?>[] scriptParamTypes;

    public QLExpressFunctionAdapter(EngineFunctionMeta meta) {
        this.meta = meta;
        this.method = meta.getMethod();
        this.method.setAccessible(true);
        Class<?>[] all = method.getParameterTypes();
        this.scriptParamTypes = meta.isInjectContext() ? Arrays.copyOfRange(all, 1, all.length) : all;
    }

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() != scriptParamTypes.length && !method.isVarArgs()) {
            throw new IllegalArgumentException(String.format(
                    "脚本函数 [%s] 参数个数不匹配，期望 %d 个，实际传入 %d 个",
                    meta.getFunctionName(), scriptParamTypes.length, parameters.size()));
        }

        Object[] scriptArgs = new Object[parameters.size()];
        for (int i = 0; i < scriptArgs.length; i++) {
            scriptArgs[i] = parameters.get(i).get();
        }
        // 类型转换只作用于脚本实参，注入的 EngineContext 不参与
        Object[] converted = ParametersTypeConvertor.cast(scriptArgs, scriptParamTypes, method.isVarArgs());

        guardSideEffect(qContext);

        Object[] finalArgs;
        if (meta.isInjectContext()) {
            finalArgs = new Object[converted.length + 1];
            finalArgs[0] = qContext.attachment().get(QLExpressEvaluator.ATTACH_ENGINE_CONTEXT);
            System.arraycopy(converted, 0, finalArgs, 1, converted.length);
        } else {
            finalArgs = converted;
        }

        try {
            return method.invoke(meta.getTargetBean(), finalArgs);
        } catch (InvocationTargetException e) {
            // 业务异常原样抛，不要被反射层包一圈埋掉
            throw e.getTargetException();
        }
    }

    /**
     * 有副作用的函数，一次脚本执行里只准调一次。
     *
     * <p>🔴 <b>在调用之前拦，而不是调用之后记账</b>：记账式实现在第二次调用<b>已经发生之后</b>
     * 才发现问题，奖已经发出去了，抛异常也收不回来。
     *
     * <p>拿不到上下文时<b>放行</b>：那说明这次执行没有 EngineContext（如引擎自检、语法预校验），
     * 不是业务调用。这里选择不拦，因为「拦错」的代价是正常业务被莫名其妙拒绝，
     * 比漏拦一个根本不是业务的调用更糟。
     */
    private void guardSideEffect(QContext qContext) {
        if (!meta.isSideEffect()) {
            return;
        }
        Object attachment = qContext.attachment().get(QLExpressEvaluator.ATTACH_ENGINE_CONTEXT);
        if (!(attachment instanceof EngineContext context)) {
            return;
        }
        String firstCalled = context.getInternal(SIDE_EFFECT_GUARD_KEY, String.class);
        if (firstCalled != null) {
            throw new BusinessException(String.format(
                    "脚本在一次执行里调用了多次有副作用的函数：已经调用过 [%s]，又要调用 [%s]。"
                            + "有副作用的函数（发奖、扣库存、动账）一次执行只准调一次，且应当是脚本的最后一步 —— "
                            + "常见写法错误是两个 if 分支各写了一次，而两个条件同时成立。",
                    firstCalled, meta.getFunctionName()));
        }
        context.bindInternal(SIDE_EFFECT_GUARD_KEY, meta.getFunctionName());
    }
}
