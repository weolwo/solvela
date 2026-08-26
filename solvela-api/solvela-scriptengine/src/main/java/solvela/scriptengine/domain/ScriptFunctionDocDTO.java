package solvela.scriptengine.domain;

import lombok.Data;

import java.util.List;

/**
 * 吐给前端 Monaco 编辑器的函数文档
 */
@Data
public class ScriptFunctionDocDTO {

    /**
     * 业务域枚举名，如 MEMBER
     */
    private String domain;

    /**
     * 业务域中文名，前端按它分组，如「会员域」
     */
    private String domainTitle;

    /**
     * 脚本里实际调用的函数名，如 member_getLevel
     */
    private String functionName;

    /**
     * 域内短名，如 getLevel
     */
    private String simpleName;

    /**
     * 中文描述
     */
    private String description;

    /**
     * 返回值类型
     */
    private String returnType;

    /**
     * 脚本侧参数列表，如 ["Long memberId", "String activityCode"]
     */
    private List<String> params;

    /**
     * 补全用的签名片段，如 member_getLevel(memberId, activityCode)
     */
    private String signature;

    /**
     * 对应的 Java 类，仅后端排查用
     */
    private String className;

    /**
     * 对应的 Java 方法名，仅后端排查用
     */
    private String methodName;
}
