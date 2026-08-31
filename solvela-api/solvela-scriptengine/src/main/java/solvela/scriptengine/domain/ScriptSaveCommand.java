package solvela.scriptengine.domain;

/**
 * 后台保存一个脚本版本。
 *
 * <p>保存<b>永远是新增一行</b>，不会改动任何已存在的版本 —— 线上正在跑的那一版
 * 在保存这一刻不受任何影响，要等到有人显式激活。
 *
 * @param scriptCode  脚本编码。已存在则作为它的新版本，不存在则新建
 * @param scriptName  脚本名称
 * @param scene       场景枚举名，决定入参与返回值契约。同一编码下<b>不允许改</b>
 * @param description 用途说明
 * @param content     脚本内容
 * @param changeLog   这一版改了什么
 * @param operator    操作人
 */
public record ScriptSaveCommand(String scriptCode, String scriptName, String scene,
                                String description, String content, String changeLog,
                                String operator) {
}
