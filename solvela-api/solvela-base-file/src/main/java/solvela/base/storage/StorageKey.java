package solvela.base.storage;

/**
 * 对象存储键。<b>构造即校验，让非法状态无法被表示。</b>
 *
 * <p>这个类型存在的唯一理由：把「路径穿越」「Windows 反斜杠」「未编码的中文」三类 bug
 * 全部收敛到一个构造函数里死掉，而不是散落在 download / delete / URL 拼接各处各防一遍。
 * 旧实现就是因为没有它，才会出现「先算 filePath 后补 File.separator，导致 Windows 上
 * 入库的 key 多一个反斜杠、文件写对了位置但 URL 是废的」这种只在特定 OS 上犯的错。
 *
 * <p><b>key 里不含任何用户输入</b>，一个字符都不行。生成规则见
 * {@code docs/文件模块-架构设计文档.md} §7.1：{@code {categoryCode}/{yyyyMM}/{dd}/{snowflake}.{ext}}，
 * 其中扩展名从嗅探出的 MIME 反推，不从用户文件名取。
 *
 * @Date 2026-08-09
 */
public record StorageKey(String value) {

    /**
     * 与 {@code t_file.storage_key varchar(200)} 对齐。DB 截断是静默的，所以这里必须先拦。
     */
    public static final int MAX_LENGTH = 200;

    public StorageKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("storageKey 不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "storageKey 超长（" + value.length() + " > " + MAX_LENGTH + "）：" + value);
        }
        if (value.charAt(0) == '/') {
            throw new IllegalArgumentException("storageKey 不能以 / 开头：" + value);
        }
        if (value.charAt(value.length() - 1) == '/') {
            throw new IllegalArgumentException("storageKey 不能以 / 结尾：" + value);
        }
        if (value.contains("//")) {
            throw new IllegalArgumentException("storageKey 不能含空路径段：" + value);
        }
        checkCharset(value);
        checkSegments(value);
    }

    /**
     * 白名单字符集。<b>用白名单而不是黑名单</b>：黑名单永远列不全（反斜杠、控制字符、
     * 各种 Unicode 空白、百分号编码残留……），而 key 本来就是系统生成的，
     * 字符集窄一点没有任何损失。
     */
    private static void checkCharset(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-' || c == '/';
            if (!ok) {
                throw new IllegalArgumentException(
                        "storageKey 含非法字符 '" + c + "'（U+" + String.format("%04X", (int) c)
                                + "），只允许 [A-Za-z0-9._/-]：" + value);
            }
        }
    }

    /**
     * {@code .} 与 {@code ..} 段的拦截。
     *
     * <p>虽然各实现落盘时还会再做一次 normalize + startsWith(root) 的纵深防御，
     * 但把它挡在类型构造这一层，意味着<b>连一个非法的 key 对象都造不出来</b> ——
     * 后面所有代码都不用再想这件事。
     */
    private static void checkSegments(String value) {
        for (String segment : value.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("storageKey 含相对路径段：" + value);
            }
        }
    }

    /**
     * 扩展名（不含点），没有则返回空串。
     */
    public String extension() {
        int slash = value.lastIndexOf('/');
        int dot = value.lastIndexOf('.');
        return dot > slash && dot < value.length() - 1 ? value.substring(dot + 1) : "";
    }

    @Override
    public String toString() {
        return value;
    }
}
