package net.lab1024.sa.base.sonicexcel.write;

import net.lab1024.sa.base.sonicexcel.meta.ColumnMeta;
import org.dhatim.fastexcel.Worksheet;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * 单元格类型路由。
 *
 * <p><b>红线：所有文本一律走 {@link Worksheet#inlineString}，禁止 {@code value(r, c, String)}。</b>
 * fastexcel 的 Workbook 没有任何关闭 shared strings 的开关，内部 StringCache 是一个
 * 无条件启用、永不清理的 HashMap —— 只要有一处调了 value(String)，那个字符串就永久驻留堆中。
 * 千万级高基数文本（订单号、地址、姓名）会直接把堆吃穿。
 * 代价是文件体积变大（重复文本不去重），换取堆占用与数据量彻底解耦。
 *
 * @Date 2026-08-08
 */
final class CellWriter {

    /**
     * xlsx 单元格文本上限。超出会产出一个 Excel 打不开的文件。
     */
    static final int MAX_CELL_CHARS = 32767;

    /**
     * IEEE 754 双精度能精确表示的十进制位数。超过这个位数的整数写成数值，Excel 显示必然失真。
     */
    private static final int MAX_EXACT_DIGITS = 15;

    private final boolean escapeFormula;

    private int truncatedCount;

    CellWriter(boolean escapeFormula) {
        this.escapeFormula = escapeFormula;
    }

    void write(Worksheet ws, int r, int c, Object value, ColumnMeta col) {
        if (value == null) {
            return;
        }
        if (col.forceText()) {
            text(ws, r, c, String.valueOf(value));
        } else {
            switch (value) {
                case String s -> text(ws, r, c, s);
                case Boolean b -> ws.value(r, c, b);
                case Number n -> number(ws, r, c, n);
                case LocalDateTime dt -> ws.value(r, c, dt);
                case LocalDate d -> ws.value(r, c, d);
                case ZonedDateTime zdt -> ws.value(r, c, zdt);
                case Date d -> ws.value(r, c, d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                // fastexcel 没有 LocalTime 重载，按文本写；日期时间列建议直接用 LocalDateTime
                case LocalTime t -> text(ws, r, c, t.toString());
                case Enum<?> e -> text(ws, r, c, e.name());
                case Collection<?> col0 -> text(ws, r, c,
                        col0.stream().map(String::valueOf).collect(Collectors.joining(",")));
                default -> text(ws, r, c, String.valueOf(value));
            }
        }
        if (col.hasFormat()) {
            ws.style(r, c).format(col.format()).set();
        }
    }

    int truncatedCount() {
        return truncatedCount;
    }

    // ------------------------------------------------------------------

    private void number(Worksheet ws, int r, int c, Number n) {
        // 手机号、身份证、订单号、雪花 ID 写成数值，Excel 会显示成 1.38E+10 —— 导出组件的经典投诉
        if (exceedsExcelPrecision(n)) {
            text(ws, r, c, plain(n));
            return;
        }
        ws.value(r, c, n);
    }

    private void text(Worksheet ws, int r, int c, String value) {
        if (value.isEmpty()) {
            return;
        }
        String s = value;
        if (s.length() > MAX_CELL_CHARS) {
            s = s.substring(0, MAX_CELL_CHARS);
            truncatedCount++;
        }
        if (escapeFormula && startsWithFormulaTrigger(s)) {
            s = "'" + s;
        }
        ws.inlineString(r, c, s);
    }

    private static boolean startsWithFormulaTrigger(String s) {
        char first = s.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }

    private static boolean exceedsExcelPrecision(Number n) {
        // Double / Float 本来就是浮点，科学计数是预期显示，不做干预
        if (n instanceof Double || n instanceof Float) {
            return false;
        }
        if (!(n instanceof BigDecimal || n instanceof BigInteger
                || n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte)) {
            return false;
        }
        String s = plain(n);
        int digits = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                digits++;
            }
        }
        return digits > MAX_EXACT_DIGITS;
    }

    private static String plain(Number n) {
        return n instanceof BigDecimal bd ? bd.toPlainString() : n.toString();
    }
}
