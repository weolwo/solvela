package sa.scriptengine.core;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.data.convert.ParametersTypeConvertor;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import sa.scriptengine.domain.EngineFunctionMeta;

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
 *       原样冒到上层，而不是被包成一层反射异常。</li>
 * </ol>
 */
public class QLExpressFunctionAdapter implements CustomFunction {

    private final EngineFunctionMeta meta;

    private final Method method;

    /** 脚本侧真正需要传的形参类型（已剔除被注入的 EngineContext） */
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
}
