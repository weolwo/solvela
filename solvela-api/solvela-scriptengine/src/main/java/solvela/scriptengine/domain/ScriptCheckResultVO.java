package solvela.scriptengine.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 脚本语法校验结果。
 *
 * <p><b>校验失败是正常返回而不是抛异常</b>：对编辑器来说「这段脚本目前有语法错误」是它每敲一个字
 * 都会遇到的常态，不是服务端故障。抛异常会让前端每次都吃到一个错误弹窗，
 * 而且行号信息藏在异常的附加字段里，前端拿不到就没法划红线。
 */
@Data
public class ScriptCheckResultVO {

    @Schema(description = "语法是否合法")
    private Boolean valid;

    @Schema(description = "错误详情，形如「第 2 行 第 12 列：xxx」。valid=true 时为 null")
    private String detail;

    public static ScriptCheckResultVO pass() {
        ScriptCheckResultVO result = new ScriptCheckResultVO();
        result.setValid(true);
        return result;
    }

    public static ScriptCheckResultVO fail(String detail) {
        ScriptCheckResultVO result = new ScriptCheckResultVO();
        result.setValid(false);
        result.setDetail(detail);
        return result;
    }
}
