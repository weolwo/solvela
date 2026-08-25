package solvela.scriptengine.loader;

import solvela.scriptengine.spi.ScriptDomain;
import solvela.scriptengine.spi.ScriptScene;

/**
 * 一个已经解析好、校验过的脚本文件。
 *
 * @param scriptCode 由路径推导，如 {@code task/streak_sign_7d}
 * @param name       文件头 {@code @name}
 * @param scene      文件头 {@code @scene}，决定入参与返回值契约
 * @param description 文件头 {@code @desc}
 * @param filePath   classpath 下的路径，如 {@code scripts/task/streak_sign_7d.ql}
 * @param content    脚本全文（含文件头注释，QL 会忽略注释）
 * @param contentHash content 的 SHA-256
 */
public record ScriptFile(
        String scriptCode,
        String name,
        ScriptScene scene,
        String description,
        String filePath,
        String content,
        String contentHash) {

    /** 域由场景推导，不单独声明 —— 能推导出来的东西不留给人写 */
    public ScriptDomain domain() {
        return scene.getDomain();
    }
}
