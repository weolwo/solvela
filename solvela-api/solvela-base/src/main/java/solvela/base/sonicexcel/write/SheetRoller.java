package solvela.base.sonicexcel.write;

import solvela.base.sonicexcel.meta.ColumnMeta;
import solvela.base.sonicexcel.meta.SheetMeta;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;
import java.util.List;

/**
 * 行位分配 + 刷盘 + 超行数自动换 Sheet + 列宽落地。
 *
 * <p>三个"不做就等于没做"的点：
 * <ul>
 *   <li><b>flush</b>：fastexcel 的 Worksheet 在 flush() 之前所有 cell 都攒在内存里，
 *       不 flush 的流式写和一次性写占用一模一样。flush 只能顺序向前，所以行号必须单线程分配。</li>
 *   <li><b>滚 Sheet</b>：xlsx 单表硬上限 1,048,576 行，不换 Sheet 的话"千万级导出"根本不成立。
 *       换 Sheet 时对旧表调 finish()，它内部会 {@code rows.clear()} 把那一整张表的行数组释放掉。</li>
 *   <li><b>列宽</b>：{@code <cols>} 是首次 flush 时一次性写出的，所以宽度必须<b>赶在第一次 flush 之前</b>
 *       落定 —— 采样够了就立刻落，没够也要在 flush / finish 前强制落。</li>
 * </ul>
 *
 * @Date 2026-08-08
 */
final class SheetRoller {

    private final Workbook workbook;
    private final SheetMeta meta;
    private final String baseName;
    private final int maxRowsPerSheet;
    private final int flushEvery;
    private final boolean freezeHeader;
    private final ColumnWidths widths;

    private Worksheet current;
    private int sheetSeq;
    private int dataRowsInSheet;
    private long totalDataRows;
    private boolean widthsApplied;

    SheetRoller(Workbook workbook, SheetMeta meta, String baseName,
                int maxRowsPerSheet, int flushEvery, boolean freezeHeader) {
        this.workbook = workbook;
        this.meta = meta;
        this.baseName = baseName;
        this.maxRowsPerSheet = maxRowsPerSheet;
        this.flushEvery = flushEvery;
        this.freezeHeader = freezeHeader;
        this.widths = new ColumnWidths(meta);
    }

    /**
     * 准备好下一行的位置（必要时换表、刷盘），返回物理行号。<b>不消费行位</b>，
     * 这样"这一行转换失败要跳过"时不会在文件里留下一个空行。
     */
    int prepareRow() throws IOException {
        ensureSheet();
        if (dataRowsInSheet >= maxRowsPerSheet) {
            applyWidths();
            current.finish();
            newSheet();
        } else if (dataRowsInSheet > 0 && dataRowsInSheet % flushEvery == 0) {
            applyWidths();
            current.flush();
        }
        return dataRowsInSheet + 1;
    }

    /**
     * 用刚写完的这一行参与列宽估算。采样满了就立刻把宽度落下去，别拖到 flush 前。
     */
    void commitRow(Object[] values) {
        dataRowsInSheet++;
        totalDataRows++;
        if (!widthsApplied) {
            widths.sample(values);
            if (widths.sampleFull()) {
                applyWidths();
            }
        }
    }

    Worksheet sheet() throws IOException {
        ensureSheet();
        return current;
    }

    long totalDataRows() {
        return totalDataRows;
    }

    void finish() throws IOException {
        ensureSheet();
        // 行数不足一次采样的小表走这条路：finish() 内部第一件事就是 flush，宽度必须抢在它前面
        applyWidths();
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
        // 换到新表要重新落一次宽度；此时采样多半已经满了，会立刻生效
        widthsApplied = false;
        writeHeader();
        if (widths.sampleFull()) {
            applyWidths();
        }
    }

    private void writeHeader() {
        List<ColumnMeta> columns = meta.columns();
        for (int c = 0; c < columns.size(); c++) {
            current.inlineString(0, c, columns.get(c).title());
            current.style(0, c).bold().set();
        }
        if (freezeHeader) {
            current.freezePane(0, 1);
        }
    }

    private void applyWidths() {
        if (widthsApplied) {
            return;
        }
        widthsApplied = true;
        for (int c = 0; c < widths.columnCount(); c++) {
            current.width(c, widths.widthOf(c));
        }
    }
}
