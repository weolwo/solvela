package sa.scriptengine.domain;

import lombok.Builder;
import lombok.Getter;
import sa.scriptengine.spi.ScriptEngineFunctionHandler;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 🚀 规则函数的元数据大一统模型
 */
@Getter
@Builder
public class EngineFunctionMeta {
    // --- 运行期执行所需资源 ---
    private final String handlerName;        // 脚本中调用的函数名
    private final String functionName;        // 脚本中调用的函数名
    private final ScriptEngineFunctionHandler targetBean;  // Spring Bean 实例
    private final Method method;      // 物理反射方法

    // --- 前端展示与文档所需资源 ---
    private final String description; // 中文描述
    private final String returnType;  // 返回值类型 (如 "BigDecimal")
    private final List<String> params;// 参数列表 (如 ["Long userId", "Double amount"])
}