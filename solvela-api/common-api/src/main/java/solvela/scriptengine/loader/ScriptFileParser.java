package solvela.scriptengine.loader;

import solvela.scriptengine.spi.ScriptScene;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本文件头解析器。
 *
 * <p><b>刻意做成纯静态函数、不依赖 Spring 也不依赖数据库</b>：
 * 解析与校验规则是这套机制里最容易写错的部分，必须能被单测直接盯住，
 * 而不是只能靠「启动一次应用看看报不报错」来验证。
 *
 * <p>期望的文件形态见 {@code resources/scripts/README.md}。
 */
public final class ScriptFileParser {

    /** classpath 下的根目录 */
    public static final String ROOT = "scripts/";

    public static final String EXTENSION = ".ql";

    /** 文件头：开头的第一段块注释 */
    private static final Pattern HEADER = Pattern.compile("\\A\\s*/\\*\\*(.*?)\\*/", Pattern.DOTALL);

    /**
     * 标签行：{@code * @name  xxx}，值可跨行续写（续行不能以 @ 开头）
     */
    private static final Pattern TAG = Pattern.compile(
            "^\\s*\\*?\\s*@(\\w+)[ \\t]+(.*(?:\\R(?!\\s*\\*?\\s*@)(?!\\s*\\*/).*)*)",
            Pattern.MULTILINE);

    private ScriptFileParser() {
    }

    /**
     * 解析一个脚本文件。
     *
     * @param filePath classpath 下的路径，必须以 {@code scripts/} 开头、{@code .ql} 结尾
     * @param content  文件全文
     * @throws IllegalStateException 任何一条规则不满足都直接抛，由加载器转成启动失败
     */
    public static ScriptFile parse(String filePath, String content) {
        String scriptCode = toScriptCode(filePath);

        Matcher header = HEADER.matcher(content);
        if (!header.find()) {
            throw fail(filePath, "缺少文件头注释。每个脚本必须以 /** ... */ 开头，"
                    + "并写明 @name / @scene / @desc，格式见 scripts/README.md");
        }
        String headerText = header.group(1);

        String name = tag(headerText, "name")
                .orElseThrow(() -> fail(filePath, "文件头缺少 @name"));
        String sceneName = tag(headerText, "scene")
                .orElseThrow(() -> fail(filePath, "文件头缺少 @scene"));
        String description = tag(headerText, "desc")
                .orElseThrow(() -> fail(filePath, "文件头缺少 @desc"));

        ScriptScene scene = ScriptScene.of(sceneName).orElseThrow(() -> fail(filePath,
                "@scene 的值 [" + sceneName + "] 不是合法场景。合法值见 ScriptScene 枚举"));

        // 目录必须等于场景所属域 —— 这条规则的存在就是为了让目录树不撒谎
        String expectedDir = scene.getDomain().getNamespace();
        String actualDir = scriptCode.contains("/") ? scriptCode.substring(0, scriptCode.indexOf('/')) : "";
        if (!expectedDir.equals(actualDir)) {
            throw fail(filePath, String.format(
                    "目录与场景不匹配。@scene=%s 属于 %s 域，文件应该放在 scripts/%s/ 下，实际在 scripts/%s/",
                    scene.name(), scene.getDomain().getTitle(), expectedDir,
                    actualDir.isEmpty() ? "(根目录)" : actualDir));
        }

        return new ScriptFile(scriptCode, name, scene, description, filePath, content, sha256(content));
    }

    /**
     * 由文件路径推导 script_code：{@code scripts/task/streak_sign_7d.ql} → {@code task/streak_sign_7d}
     */
    public static String toScriptCode(String filePath) {
        String path = filePath.replace('\\', '/');
        int rootAt = path.indexOf(ROOT);
        if (rootAt < 0) {
            throw fail(filePath, "脚本文件必须位于 classpath 的 " + ROOT + " 目录下");
        }
        String relative = path.substring(rootAt + ROOT.length());
        if (!relative.endsWith(EXTENSION)) {
            throw fail(filePath, "脚本文件扩展名必须是 " + EXTENSION);
        }
        String code = relative.substring(0, relative.length() - EXTENSION.length());
        if (code.isBlank()) {
            throw fail(filePath, "脚本文件名不能为空");
        }
        return code;
    }

    public static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制要求实现的算法，走不到这里
            throw new IllegalStateException(e);
        }
    }

    private static java.util.Optional<String> tag(String headerText, String tagName) {
        Matcher matcher = TAG.matcher(headerText);
        while (matcher.find()) {
            if (tagName.equals(matcher.group(1))) {
                String value = matcher.group(2).lines()
                        // 续行要剥掉行首的 " * " 装饰
                        .map(line -> line.replaceFirst("^\\s*\\*?\\s*", ""))
                        .map(String::strip)
                        .filter(line -> !line.isEmpty())
                        .reduce((a, b) -> a + " " + b)
                        .orElse("");
                return value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(value);
            }
        }
        return java.util.Optional.empty();
    }

    private static IllegalStateException fail(String filePath, String reason) {
        return new IllegalStateException("脚本文件 [" + filePath + "] 不合法：" + reason);
    }
}
