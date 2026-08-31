package solvela.scriptengine.domain;

import org.springframework.util.Assert;

/**
 * 一段可执行的脚本。
 *
 * <p>没有 {@code of(...)} 这样的短名默认工厂：{@link #trusted} 与 {@link #untrusted}
 * 的差别有内存与安全后果，必须在每个调用点显式选择，不能靠"哪个名字短就用哪个"决定。
 *
 * @param name    脚本标识，只用于日志与报错定位（例如 "task/sign_streak_7"）
 * @param content 脚本内容
 * @param source  内容来源，见 {@link ScriptSource}
 */
public record ExecutableScript(String name, String content, ScriptSource source) {

    public ExecutableScript {
        Assert.hasText(name, "脚本名称不能为空！请务必提供明确的规则标识以供排查！");
        Assert.hasText(content, "脚本内容不能为空！");
        Assert.notNull(source, "脚本来源不能为空！必须显式声明 TRUSTED 或 UNTRUSTED");
    }

    /**
     * 受控脚本：内容来自 {@code t_script} 里已激活的版本（集合有界，可安全缓存编译产物）
     */
    public static ExecutableScript trusted(String name, String content) {
        return new ExecutableScript(name, content, ScriptSource.TRUSTED);
    }

    /**
     * 不受控脚本：内容由请求直接决定（在线试跑、动态拼装）
     */
    public static ExecutableScript untrusted(String name, String content) {
        return new ExecutableScript(name, content, ScriptSource.UNTRUSTED);
    }
}
