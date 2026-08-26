package solvela.scriptengine.domain;

import lombok.Builder;
import lombok.Getter;
import solvela.scriptengine.spi.ScriptDomain;
import solvela.scriptengine.spi.ScriptFunctionHandler;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 一个脚本函数的全部元数据：既供运行期反射调用，也供前端编辑器生成文档
 */
@Getter
@Builder
public class EngineFunctionMeta {

    // --- 身份 ---

    /**
     * 所属业务域
     */
    private final ScriptDomain domain;

    /**
     * 注册进引擎的全局唯一函数名，如 member_getLevel
     */
    private final String functionName;

    /**
     * 域内短名，如 getLevel
     */
    private final String simpleName;

    // --- 运行期执行所需资源 ---

    /**
     * Spring Bean 实例
     */
    private final ScriptFunctionHandler targetBean;

    /**
     * 物理反射方法
     */
    private final Method method;

    /**
     * 首参是否为 EngineContext。为 true 时引擎会自动注入执行上下文，
     * 脚本侧的实参从方法的第 2 个形参开始对应。
     */
    private final boolean injectContext;

    // --- 前端展示与文档所需资源 ---

    /**
     * 中文描述
     */
    private final String description;

    /**
     * 返回值类型简名
     */
    private final String returnType;

    /**
     * 脚本侧需要传的参数列表（已剔除自动注入的 EngineContext）
     */
    private final List<String> params;

    /**
     * 声明该函数的 Java 类全限定名，排查冲突用
     */
    public String declaringClassName() {
        return targetBean.getClass().getName();
    }
}
