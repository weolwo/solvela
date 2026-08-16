package sa.scriptengine.core;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.Value;
import com.alibaba.qlexpress4.runtime.data.convert.ParametersTypeConvertor;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import sa.scriptengine.domain.EngineFunctionMeta;
import sa.scriptengine.spi.ScriptEngineFunctionHandler;

import java.lang.reflect.Method;

/**
 * 专为 QLExpress 4.1.0 打造的优雅反射桥接器
 */
public class QLExpressReflectionWrapper implements CustomFunction {
    private final ScriptEngineFunctionHandler targetBean;
    private final Method method;

    public QLExpressReflectionWrapper(EngineFunctionMeta meta) {
        this.targetBean = meta.getTargetBean();
        this.method = meta.getMethod();
        this.method.setAccessible(true);
    }

    @Override
    public Object call(QContext qContext, Parameters parameters)
            throws Throwable {

        Object[] params = new Object[parameters.size()];
        for (int i = 0; i < params.length; i++) {
            Value v = parameters.get(i);
            params[i] = v.get();
        }
        Object[] convertResult = ParametersTypeConvertor.cast(params, method.getParameterTypes(), method.isVarArgs());
        return method.invoke(targetBean, convertResult);
    }
}