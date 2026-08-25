package solvela.base.common.exception;

import solvela.base.common.exception.BusinessException;

/**
 * 🚀 规则引擎专属异常 (隔离底层 QLException, AviatorException)
 */
public class EngineScriptException extends BusinessException {

    // 可以扩展存放出错的行号、错误关键字等
    private String scriptErrorDetail;

    public EngineScriptException(String message, String scriptErrorDetail) {
        super(message);
        this.scriptErrorDetail = scriptErrorDetail;
    }

    public String getScriptErrorDetail() {
        return scriptErrorDetail;
    }
}
