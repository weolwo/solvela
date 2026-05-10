package net.lab1024.sa.scriptengine.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.util.Assert;

@Data
@Builder
public class ExecutableScript {

    private final String name;    // 规则名称 (例如："双11美妆会场满减规则")
    private final String content; // 脚本内容

    private ExecutableScript(String name, String content) {
        Assert.hasText(name, "脚本名称不能为空！请务必提供明确的规则标识以供排查！");
        Assert.hasText(content, "脚本内容不能为空！");
        this.name = name;
        this.content = content;
    }

    /**
     * 优雅的静态工厂方法
     */
    public static ExecutableScript of(String name, String content) {
        return new ExecutableScript(name, content);
    }

}
