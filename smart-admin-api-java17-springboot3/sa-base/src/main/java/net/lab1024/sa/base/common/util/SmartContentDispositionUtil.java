package net.lab1024.sa.base.common.util;

import java.nio.charset.StandardCharsets;

/**
 * {@code Content-Disposition} 头的构造（RFC 6266 + RFC 8187）。
 *
 * <p><b>为什么不能用 {@code URLEncoder.encode}</b>（旧实现用的就是它）：那是
 * {@code application/x-www-form-urlencoded} 编码，不是 RFC 3986/8187 的 percent-encoding。
 * 三个具体后果：
 * <ul>
 *   <li>空格编成 {@code +}（旧代码只好手工 {@code replaceAll("\\+", "%20")} 打补丁）</li>
 *   <li>{@code * ' ( ) ! ~} 的处理与规范不一致</li>
 *   <li><b>逗号和分号不转义</b> —— 文件名里带分号就能把这个 header 撕成两截</li>
 * </ul>
 *
 * <p><b>为什么两种写法都要给</b>：{@code filename*} 是现代浏览器认的，
 * 而 {@code filename=} 是给不认识 {@code filename*} 的老 UA 的降级路径。
 * RFC 6266 §4.3 明确建议同时提供，且 {@code filename*} 必须放在后面 ——
 * 两个都认识的 UA 以后出现的为准。
 *
 * <pre>
 * Content-Disposition: attachment; filename="download.png"; filename*=UTF-8''%E5%B9%B4%E4%BC%9A.png
 * </pre>
 *
 * <p>刻意<b>不用 Spring 的 {@code ContentDisposition}</b>：它的 {@code toString()} 是
 * 「有 charset 就只输出 {@code filename*}、没有就只输出 {@code filename=}」的二选一，
 * 给不出上面这种双写法。
 *
 * @Date 2026-08-10
 */
public final class SmartContentDispositionUtil {

    /**
     * RFC 8187 的 attr-char。这一串<b>不含</b>逗号、分号、引号、反斜杠 ——
     * 正是它们能撕裂 header，所以必须落到百分号编码那一边。
     */
    private static final String ATTR_CHAR = "!#$&+-.^_`|~";

    /**
     * 降级文件名里允许原样保留的字符。比 attr-char 宽松（允许空格），
     * 因为它出现在 quoted-string 里；但同样排除引号、反斜杠、分号、逗号。
     */
    private static final String FALLBACK_SAFE_EXTRA = " ._-";

    /**
     * 整个文件名都不可见（比如纯中文）时的兜底基名。
     */
    private static final String FALLBACK_BASE_NAME = "download";

    private SmartContentDispositionUtil() {
    }

    /**
     * 附件下载。
     */
    public static String attachment(String fileName) {
        return build("attachment", fileName);
    }

    /**
     * 内联展示（图片 / PDF 在线预览）。
     *
     * <p>同一个对象既能 attachment 又能 inline，正是「disposition 不该在上传时烧进对象元数据」
     * 的原因 —— 烧进去之后想换就得重传或 copyObject。
     */
    public static String inline(String fileName) {
        return build("inline", fileName);
    }

    // ------------------------------------------------------------------

    private static String build(String type, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return type;
        }
        return type
                + "; filename=\"" + asciiFallback(fileName) + "\""
                + "; filename*=UTF-8''" + rfc8187(fileName);
    }

    /**
     * RFC 8187 的 percent-encoding：attr-char 原样，其余按 UTF-8 字节逐个 {@code %XX}。
     */
    private static String rfc8187(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // 必须按无符号处理：byte 是有符号的，中文的高位字节是负数
            int c = b & 0xFF;
            if (isAlphaNum(c) || ATTR_CHAR.indexOf(c) >= 0) {
                sb.append((char) c);
            } else {
                sb.append('%').append(hex(c >> 4)).append(hex(c & 0x0F));
            }
        }
        return sb.toString();
    }

    /**
     * 降级文件名：非 ASCII 与危险字符一律换成下划线，<b>扩展名尽量保住</b> ——
     * 老 UA 拿到的名字可以不好看，但不能没有扩展名，否则用户双击打不开。
     */
    private static String asciiFallback(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot + 1) : "";

        String safeBase = sanitize(base);
        String safeExt = sanitize(ext).replace(" ", "");
        if (safeBase.isBlank() || safeBase.chars().allMatch(c -> c == '_')) {
            safeBase = FALLBACK_BASE_NAME;
        }
        return safeExt.isEmpty() ? safeBase : safeBase + "." + safeExt;
    }

    private static String sanitize(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean safe = c < 0x80 && !Character.isISOControl(c)
                    && (isAlphaNum(c) || FALLBACK_SAFE_EXTRA.indexOf(c) >= 0);
            sb.append(safe ? c : '_');
        }
        return sb.toString().trim();
    }

    private static boolean isAlphaNum(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static char hex(int nibble) {
        return Character.toUpperCase(Character.forDigit(nibble, 16));
    }
}
