package net.lab1024.sa.scriptengine.domain;

import lombok.Data;

import java.util.List;

@Data
public class ScriptFunctionDocDTO {

    private String handlerName; // 处理器名字

    private String functionName; // 脚本里用的名字

    private String description;  // 中文描述

    private String returnType;   // 返回值类型

    private String className;    // 属于哪个 Java 类

    private String methodName;   // 对应的 Java 方法名

    private List<String> params; // 参数类型列表 (例如: ["Long", "String"])
}