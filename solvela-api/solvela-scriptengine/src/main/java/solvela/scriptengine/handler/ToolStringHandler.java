package solvela.scriptengine.handler;

import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.exception.BusinessException;
import solvela.scriptengine.annotation.ScriptFunction;
import solvela.scriptengine.spi.ScriptDomain;
import solvela.scriptengine.spi.ScriptFunctionHandler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 内置工具：字符串与标量取值。注册后脚本里以 {@code tool_xxx} 调用。
 *
 * <p>脚本里 {@code s.length()}、{@code s.trim()} 这类 Java 方法调用<b>全都不通</b>
 * （引擎跑在隔离策略下，见 {@code ToolCollectionHandler} 的类注释），
 * 所以字符串处理只能走这里的函数。
 *
 * <h3>🔴 参数为什么是 {@code Object} 而不是 {@code String}</h3>
 * QL 的实参类型是脚本决定的：{@code memberId} 是 Long，算术结果是 BigDecimal，
 * {@code payload['x']} 是 Object。声明成 {@code String} 的话，传非字符串进来会被
 * {@code ObjTypeConvertor} 判成「转不了」而<b>静默变成 null</b> —— 不报错、不警告。
 * 所以这里统一收 {@code Object}，自己用 {@link #str} 归一。
 */
@Component
public class ToolStringHandler implements ScriptFunctionHandler {

    /**
     * 单次调用能处理的文本长度上限。防的是脚本把一整个 payload 拼进字符串再反复切
     */
    private static final int MAX_LENGTH = 10_000;

    @Override
    public ScriptDomain domain() {
        return ScriptDomain.TOOL;
    }

    // ------------------------------------------------------------------
    // 判空与取值
    // ------------------------------------------------------------------

    @ScriptFunction(name = "isBlank", description = "判断文本是否为 null、空串或只包含空白字符")
    public Boolean isBlank(Object value) {
        return value == null || str(value).isBlank();
    }

    @ScriptFunction(name = "isNotBlank", description = "有内容（不是 null、不是空串、不全是空白）")
    public Boolean isNotBlank(Object value) {
        return !isBlank(value);
    }

    /**
     * 任意值转字符串，null 转成空串。
     *
     * <p>拼字符串时用它兜底：{@code 'key:' + maybeNull} 在 QL 里会拼出 {@code "key:null"} 这种串，
     * 拿去当缓存键就成了一个永远命中不了、也永远查不出来的键。
     */
    @ScriptFunction(name = "str", description = "任意值转字符串，null 转成空串（而不是 \"null\" 这四个字）")
    public String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @ScriptFunction(name = "defaultIfBlank", description = "文本为空（null/空串/全空白）时返回默认值，否则返回它本身")
    public String defaultIfBlank(Object value, String defaultValue) {
        return isBlank(value) ? defaultValue : str(value);
    }

    /**
     * 安全转数字。
     *
     * <p>🔴 {@code params['times']} 拿到的是 Object，直接 {@code > 3} 在类型不一致时会静默判 false。
     * 转不了数字就用默认值，把这条路堵上。
     */
    @ScriptFunction(name = "num",
            description = "安全转数字，转不了（null、空串、不是数字）就返回默认值。"
                    + "如 tool_num(params['times'], 1)。用来接前端传来的参数")
    public BigDecimal num(Object value, BigDecimal defaultValue) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = str(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ------------------------------------------------------------------
    // 变形
    // ------------------------------------------------------------------

    @ScriptFunction(name = "trim", description = "去掉首尾空白，null 返回空串")
    public String trim(Object value) {
        return str(value).trim();
    }

    @ScriptFunction(name = "upper", description = "转大写，null 返回空串")
    public String upper(Object value) {
        return str(value).toUpperCase();
    }

    @ScriptFunction(name = "lower", description = "转小写，null 返回空串")
    public String lower(Object value) {
        return str(value).toLowerCase();
    }

    @ScriptFunction(name = "substring",
            description = "安全截取字符串前 N 个字符，超长不报错，null 返回空串")
    public String substring(Object value, Integer maxLength) {
        if (value == null || maxLength == null || maxLength <= 0) {
            return "";
        }
        return SolvelaStringUtil.truncate(str(value), maxLength);
    }

    @ScriptFunction(name = "replace",
            description = "把文本里所有的 target 换成 replacement（纯文本替换，不是正则）")
    public String replace(Object value, String target, String replacement) {
        String text = checkLength(str(value), "tool_replace");
        if (target == null || target.isEmpty()) {
            return text;
        }
        return text.replace(target, replacement == null ? "" : replacement);
    }

    /**
     * 按分隔符切成列表。
     *
     * <p>分隔符是<b>纯文本</b>不是正则 —— 运营写 {@code tool_split(s, '|')} 时不该被
     * 「竖线在正则里是或」这件事伤到，那个坑连 Java 开发都常年踩。
     */
    @ScriptFunction(name = "split",
            description = "按分隔符切成列表，分隔符是纯文本不是正则（写 '|' 就是竖线本身）。null 返回空列表")
    public List<Object> split(Object value, String separator) {
        String text = checkLength(str(value), "tool_split");
        List<Object> result = new ArrayList<>();
        if (text.isEmpty()) {
            return result;
        }
        if (separator == null || separator.isEmpty()) {
            throw new BusinessException("tool_split 的分隔符不能为空");
        }
        // -1：保留末尾的空串，"a,b," 切出来是 3 段而不是 2 段。
        // 少一段的话，按位置取值的脚本会静默取错
        result.addAll(Arrays.asList(text.split(Pattern.quote(separator), -1)));
        return result;
    }

    // ------------------------------------------------------------------
    // 判定
    // ------------------------------------------------------------------

    @ScriptFunction(name = "startsWith", description = "文本是否以某段开头")
    public Boolean startsWith(Object value, String prefix) {
        return prefix != null && str(value).startsWith(prefix);
    }

    @ScriptFunction(name = "endsWith", description = "文本是否以某段结尾")
    public Boolean endsWith(Object value, String suffix) {
        return suffix != null && str(value).endsWith(suffix);
    }

    @ScriptFunction(name = "equalsIgnoreCase",
            description = "忽略大小写比较两段文本，两边都是 null 算相等")
    public Boolean equalsIgnoreCase(Object value, Object other) {
        if (value == null || other == null) {
            return value == other;
        }
        return str(value).equalsIgnoreCase(str(other));
    }

    /**
     * 脱敏。日志里要打手机号、账号这类东西时必须先过一遍。
     */
    @ScriptFunction(name = "mask",
            description = "把第 start 到 end 位之间的字符换成 *（从 0 数起，含 start 不含 end）。"
                    + "如 tool_mask(phone, 3, 7) 得到 138****8000。打日志前先过一遍")
    public String mask(Object value, Integer start, Integer end) {
        String text = str(value);
        if (text.isEmpty() || start == null || end == null) {
            return text;
        }
        return SolvelaStringUtil.hide(text, start, end);
    }

    // ------------------------------------------------------------------

    private String checkLength(String text, String functionName) {
        if (text.length() > MAX_LENGTH) {
            throw new BusinessException(functionName + " 收到的文本有 " + text.length()
                    + " 个字符，超过上限 " + MAX_LENGTH + "。脚本里不该处理这么大的文本");
        }
        return text;
    }
}
