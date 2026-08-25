package solvela.base.sonicexcel.write;

import solvela.base.sonicexcel.meta.ColumnMeta;
import solvela.base.sonicexcel.meta.SheetMeta;

import java.util.List;

/**
 * 列宽估算。
 *
 * <p>fastexcel 只有 {@code ws.width(col, w)}，<b>没有 auto-size</b>；不处理的话中文列全是 {@code ####}。
 *
 * <p>约束是硬的：<b>{@code <cols>} 在第一次 flush 时一次性写出</b>，之后再调 width 没有任何效果。
 * 所以只能采样前若干行 —— 采样上限必须小于 flushEvery，否则第一批还没采完就已经写出去了。
 *
 * @Date 2026-08-08
 */
final class ColumnWidths {

    private static final int MIN_WIDTH = 8;
    private static final int MAX_WIDTH = 60;
    /**
     * 留一点余量，免得内容正好贴着边框。
     */
    private static final int PADDING = 2;

    /**
     * 采样行数。取 100 是因为再多对结果几乎没有影响，而它必须远小于 flushEvery（默认 1000）。
     */
    static final int SAMPLE_ROWS = 100;

    private final int[] widths;
    private final boolean[] fixed;
    private int sampled;

    ColumnWidths(SheetMeta meta) {
        List<ColumnMeta> columns = meta.columns();
        this.widths = new int[columns.size()];
        this.fixed = new boolean[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            ColumnMeta col = columns.get(c);
            if (col.width() > 0) {
                widths[c] = col.width();
                fixed[c] = true;
            } else {
                // 起点是表头本身，数据再宽也只会往上顶
                widths[c] = displayWidth(col.title());
            }
        }
    }

    /**
     * 喂一行数据参与估算。超过采样上限后直接返回，不再有开销。
     */
    void sample(Object[] values) {
        if (sampled >= SAMPLE_ROWS) {
            return;
        }
        sampled++;
        for (int c = 0; c < widths.length && c < values.length; c++) {
            if (fixed[c] || values[c] == null) {
                continue;
            }
            int w = displayWidth(String.valueOf(values[c]));
            if (w > widths[c]) {
                widths[c] = w;
            }
        }
    }

    boolean sampleFull() {
        return sampled >= SAMPLE_ROWS;
    }

    int widthOf(int column) {
        // 显式写了 @SonicTitle(width=12) 就该原样是 12：既不补余量也不钳制，
        // 用户明确指定的东西被框架偷偷改掉是最难排查的那类问题
        if (fixed[column]) {
            return widths[column];
        }
        return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, widths[column] + PADDING));
    }

    int columnCount() {
        return widths.length;
    }

    /**
     * 中文（以及全角标点）在 Excel 里约占两个字符宽。
     */
    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) > 0xFF ? 2 : 1;
        }
        return w;
    }
}
