package solvela.base.sonicexcel;

/**
 * SonicExcel 统一异常。
 *
 * <p>元数据解析失败（列序冲突、访问器生成不出来）属于编码错误，在**启动/首次使用期**抛出；
 * 读写期的数据问题走 {@link solvela.base.sonicexcel.error.SonicErrorPolicy}，
 * 只有 FailFast 策略才会转成本异常。
 *
 * @Date 2026-08-08
 */
public class SonicExcelException extends RuntimeException {

    public SonicExcelException(String message) {
        super(message);
    }

    public SonicExcelException(String message, Throwable cause) {
        super(message, cause);
    }
}
