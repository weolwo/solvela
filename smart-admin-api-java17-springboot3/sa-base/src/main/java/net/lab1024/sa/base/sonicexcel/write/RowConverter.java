package net.lab1024.sa.base.sonicexcel.write;

import net.lab1024.sa.base.sonicexcel.SonicExcelException;
import net.lab1024.sa.base.sonicexcel.converter.SonicContext;
import net.lab1024.sa.base.sonicexcel.error.SonicErrorPolicy;
import net.lab1024.sa.base.sonicexcel.error.SonicRowError;
import net.lab1024.sa.base.sonicexcel.meta.ColumnMeta;
import net.lab1024.sa.base.sonicexcel.meta.SheetMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 实体 → 一行的值数组，按 {@link SonicErrorPolicy} 处置转换异常。
 *
 * <p>xlsx 和 CSV 两条导出通道共用这一份逻辑，省得错误策略在两个地方各写一遍、慢慢长歪。
 *
 * <p><b>先把整行的值全部算出来再落笔</b>：这样"某一列转换炸了要跳过整行"才是可实现的 ——
 * 一旦开始往输出流写，已写的单元格就撤不回来了。
 *
 * @Date 2026-08-08
 */
final class RowConverter<T> {

    private final SheetMeta meta;
    private final SonicErrorPolicy errorPolicy;
    private final List<SonicRowError> errors = new ArrayList<>();
    private long skippedRows;

    RowConverter(SheetMeta meta, SonicErrorPolicy errorPolicy) {
        this.meta = meta;
        this.errorPolicy = errorPolicy;
    }

    /**
     * @return null 表示这一行按策略被跳过
     */
    Object[] convert(T row, int rowIndex) {
        List<ColumnMeta> columns = meta.columns();
        Object[] values = new Object[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            ColumnMeta col = columns.get(c);
            try {
                Object raw = col.getter().apply(row);
                values[c] = col.converter().exportConvert(raw,
                        new SonicContext(rowIndex, c, col.title(), col.javaType(), col.element(), row));
            } catch (Exception e) {
                handle(new SonicRowError(rowIndex, col.title(), null,
                        e.getClass().getSimpleName() + ": " + e.getMessage()), e);
                skippedRows++;
                return null;
            }
        }
        return values;
    }

    private void handle(SonicRowError error, Exception cause) {
        switch (errorPolicy) {
            case SonicErrorPolicy.FailFast ignored ->
                    throw new SonicExcelException("导出失败，" + error.describe(), cause);
            case SonicErrorPolicy.Collect(int maxErrors) -> {
                errors.add(error);
                if (errors.size() > maxErrors) {
                    throw new SonicExcelException("导出错误数超过上限 " + maxErrors
                            + "，已熔断。首条：" + errors.getFirst().describe(), cause);
                }
            }
            case SonicErrorPolicy.Skip ignored -> {
                // 静默跳过，只在收尾时打一条汇总，不是每行一条日志
            }
        }
    }

    List<SonicRowError> errors() {
        return Collections.unmodifiableList(errors);
    }

    long skippedRows() {
        return skippedRows;
    }
}
