package solvela.base.common.util;

import java.util.Map;

/**
 * {@code ${key}} 占位符替换，替代 {@code commons-text} 的 {@code StringSubstitutor}。
 *
 * <p>项目里只有邮件模板和站内信模板两处用它，且模板里只出现过最朴素的 {@code ${key}}，
 * 没用过默认值语法。为这两处留一个 259KB 的依赖不划算（而且除我们之外没人依赖 commons-text）。
 *
 * <h3>与 StringSubstitutor 的口径对照</h3>
 * <ul>
 *   <li>✅ {@code ${key}} → 参数值，行为一致</li>
 *   <li>✅ <b>解析不到的占位符原样保留</b>（key 不存在、或值为 null），行为一致 ——
 *       模板是外部传进来的字符串，里面出现非参数的 {@code ${...}} 很正常，不能吞掉</li>
 *   <li>✅ {@code $${key}} 转义成字面量 {@code ${key}}，行为一致；
 *       而单独的 {@code $} 或 {@code $$x}（后面不跟 <code>{</code>）原样保留，同样一致</li>
 *   <li>🔀 <b>不对替换后的值做递归解析</b>。StringSubstitutor 默认会把替换进去的值再扫一遍，
 *       值里带 {@code ${...}} 会被继续替换。这里刻意不这么做：模板参数常常来自用户数据，
 *       递归解析等于给了一条模板注入的路。这是一处<b>有意的口径收紧</b>，已固化成测试</li>
 * </ul>
 *
 * @Date 2026-08-08
 */
public final class SolvelaTemplateUtil {

    private SolvelaTemplateUtil() {
    }

    public static String render(String template, Map<String, ?> params) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        StringBuilder out = new StringBuilder(template.length() + 32);
        int index = 0;
        int length = template.length();
        while (index < length) {
            char current = template.charAt(index);

            // $${ 是转义：吐出字面量 ${，后面的内容按普通文本走
            if (current == '$' && index + 2 < length
                    && template.charAt(index + 1) == '$' && template.charAt(index + 2) == '{') {
                out.append("${");
                index += 3;
                continue;
            }

            if (current == '$' && index + 1 < length && template.charAt(index + 1) == '{') {
                int close = template.indexOf('}', index + 2);
                if (close > 0) {
                    Object value = params == null ? null : params.get(template.substring(index + 2, close));
                    if (value != null) {
                        out.append(value);
                    } else {
                        // 解析不到就原样保留整个占位符，不能吞成空串
                        out.append(template, index, close + 1);
                    }
                    index = close + 1;
                    continue;
                }
            }

            out.append(current);
            index++;
        }
        return out.toString();
    }
}
