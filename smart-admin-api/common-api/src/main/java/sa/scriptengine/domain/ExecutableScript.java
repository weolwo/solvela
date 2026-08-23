package sa.scriptengine.domain;

import org.springframework.util.Assert;

/**
 * 一段可执行的脚本。
 *
 * @param name      脚本标识，只用于日志与报错定位（例如 "task/sign_streak_7"）
 * @param content   脚本内容
 * @param cacheable 是否允许底层引擎缓存它的编译产物。
 *                  <p><b>这个开关是防 OOM 的关键。</b>QLExpress 4.x 的编译缓存以脚本原文为 key
 *                  且没有容量上限。项目内 git 管理的脚本数量有限、内容稳定，缓存收益极高；
 *                  而在线试跑、前端临时拼装这类<b>内容随机</b>的脚本一旦进了缓存就再也出不来，
 *                  必须用 {@link #ofAdHoc} 关掉。
 */
public record ExecutableScript(String name, String content, boolean cacheable) {

    public ExecutableScript {
        Assert.hasText(name, "脚本名称不能为空！请务必提供明确的规则标识以供排查！");
        Assert.hasText(content, "脚本内容不能为空！");
    }

    /**
     * 常规脚本：内容来自项目内受控的脚本文件，开启编译缓存
     */
    public static ExecutableScript of(String name, String content) {
        return new ExecutableScript(name, content, true);
    }

    /**
     * 临时脚本：内容不受控（在线试跑、动态拼装），关闭编译缓存
     */
    public static ExecutableScript ofAdHoc(String name, String content) {
        return new ExecutableScript(name, content, false);
    }
}
