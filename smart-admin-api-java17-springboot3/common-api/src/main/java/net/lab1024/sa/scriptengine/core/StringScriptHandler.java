package net.lab1024.sa.scriptengine.core;

import net.lab1024.sa.base.module.support.scriptengine.annotation.ScriptFunction;
import net.lab1024.sa.base.module.support.scriptengine.annotation.ScriptFunctionGroup;
import net.lab1024.sa.scriptengine.spi.EngineContext;
import net.lab1024.sa.scriptengine.spi.ScriptEngineFunctionHandler;
import org.springframework.stereotype.Component;

/**
 * 🚀 内置工具：字符串处理器
 */
@ScriptFunctionGroup(value = "字符串处理器")
@Component
public class StringScriptHandler implements ScriptEngineFunctionHandler {

    @ScriptFunction(name = "_isBlank", description = "判断文本是否为空或者只包含空格")
    public Boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }


    @ScriptFunction(name = "_substring", description = "安全截取字符串，超长不会报错")
    public String substring(String str, Integer maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength);
    }
}