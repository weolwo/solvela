package sa.scriptengine.handler;

import org.springframework.stereotype.Component;
import sa.scriptengine.annotation.ScriptFunction;
import sa.scriptengine.spi.ScriptDomain;
import sa.scriptengine.spi.ScriptFunctionHandler;

/**
 * 内置工具：字符串处理。注册后脚本里以 {@code tool_xxx} 调用。
 */
@Component
public class ToolStringHandler implements ScriptFunctionHandler {

    @Override
    public ScriptDomain domain() {
        return ScriptDomain.TOOL;
    }

    @ScriptFunction(name = "isBlank", description = "判断文本是否为 null、空串或只包含空白字符")
    public Boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    @ScriptFunction(name = "substring", description = "安全截取字符串前 N 个字符，超长不报错，null 返回空串")
    public String substring(String str, Integer maxLength) {
        if (str == null || maxLength == null || maxLength <= 0) {
            return "";
        }
        return str.length() <= maxLength ? str : str.substring(0, maxLength);
    }
}
