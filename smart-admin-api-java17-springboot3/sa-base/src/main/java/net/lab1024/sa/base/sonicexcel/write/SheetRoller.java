package net.lab1024.sa.base.sonicexcel.write;

import net.lab1024.sa.base.sonicexcel.meta.ColumnMeta;
import net.lab1024.sa.base.sonicexcel.meta.SheetMeta;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;

/**
 * 行位分配 + 刷盘 + 超行数自动换 Sheet。
 *
 * <p>两个"不做就等于没做"的点：
 * <ul>
 *   <li><b>flush</b>：fastexcel 的 Worksheet 在 flush() 之前所有 cell 都攒在内存里，
 *       不 flush 的流式写和一次性写占用一模一样。flush 只能顺序向前，所以行号必须单线程分配。</li>
 *   <li><b>滚 Sheet</b>：xlsx 单表硬上限 1,048,576 行，不换 Sheet 的话"千万级导出"根本不成立。
 *       换 Sheet 时对旧表调 finish()，它内部会 {@code rows.clear()} 把那一整张表的行数组释放掉。</li>
 * </ul>
 *
 * @Date 2026-08-08
 */
final class SheetRoller {

    private static final int MIN_WIDTH = 8;
    private static final int MAX_WIDTH = 60;
    /**
     * 中文字符在 Excel 里约占两个字符宽，另加一点余量避免贴边。
     */
    private static final int WIDTH_PADDING = 2;

    private final Workbook workbook;
    private final SheetMeta meta;
    private final String baseName;
    private final int maxRowsPerSheet;
    private final int flushEvery;
    private final boolean freezeHeader;

    private Worksheet current;
    private int sheetSeq;
    private int dataRowsInSheet;
    private long totalDataRows;

    SheetRoller(Workbook workbook, SheetMeta meta, String baseName,
                int maxRowsPerSheet, int flushEvery, boolean freezeHeader) {
        this.workbook = workbook;
        this.meta = meta;
        this.baseName = baseName;
        this.maxRowsPerSheet = maxRowsPerSheet;
        this.flushEvery = flushEvery;
        this.freezeHeader = freezeHeader;
    }

    /**
     * 准备好下一行的位置（必要时换表、刷盘），返回物理行号。<b>不消费行位</b>，
     * 这样"这一行转换失败要跳过"时不会在文件里留下一个空行。
     */
    int prepareRow() throws IOException {
        ensureSheet();
        if (dataRowsInSheet >= maxRowsPerSheet) {
            current.finish();
            newSheet();
        } else if (dataRowsInSheet > 0 && dataRowsInSheet % flushEvery == 0) {
            current.flush();
        }
        return dataRowsInSheet + 1;
    }

    void commitRow() {
        dataRowsInSheet++;
        totalDataRows++;
    }

    Worksheet sheet() throws IOException {
        ensureSheet();
        return current;
    }

    long totalDataRows() {
        return totalDataRows;
    }

    int sheetCount() {
        return sheetSeq;
    }

    void finish() throws IOException {
        ensureSheet();
        current.finish();
    }

    // ------------------------------------------------------------------

    private void ensureSheet() throws IOException {
        if (current == null) {
            newSheet();
        }
    }

    private void newSheet() throws IOException {
        sheetSeq++;
        // 名称非法字符与 31 字符上限由 fastexcel 的 newWorksheet 自己处理，这里不重复裁剪
        current = workbook.newWorksheet(sheetSeq == 1 ? baseName : baseName + "_" + sheetSeq);
        dataRowsInSheet = 0;
        writeHeader();
    }

    private void writeHeader() {
        java.util.List<ColumnMeta> columns = meta.columns();
        for (int c = 0; c < columns.size(); c++) {
            ColumnMeta col = columns.get(c);
            current.inlineString(0, c, col.title());
            current.style(0, c).bold().set();
            // 列宽必须在第一次 flush 之前设好 —— flush 会把 <cols> 一次性写出去
            current.width(c, resolveWidth(col));
        }
        if (freezeHeader) {
            current.freezePane(0, 1);
        }
    }

    /**
     * 第①档只按表头文本估算，够用且零成本；按数据内容自适应放在第④档。
     * 不处理的话中文列全是 ####。
     */
    private static int resolveWidth(ColumnMeta col) {
        if (col.width() > 0) {
            return col.width();
        }
        int w = displayWidth(col.title()) + WIDTH_PADDING;
        return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, w));
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) > 0xFF ? 2 : 1;
        }
        return w;
    }
}
