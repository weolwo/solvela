package sa.base.sonicexcel.write;

import sa.base.sonicexcel.error.SonicErrorPolicy;
import sa.base.sonicexcel.error.SonicRowError;
import sa.base.sonicexcel.meta.ColumnMeta;
import sa.base.sonicexcel.meta.MetaResolver;
import sa.base.sonicexcel.meta.SheetMeta;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * 写门面。
 *
 * <p>三种用法：
 * <pre>{@code
 * // A 千万级流式（配合数据库 Cursor）
 * try (var b = SonicExcel.write(os, Foo.class).sheet("数据")) { b.doWrite(cursorStream); }
 *
 * // B 日常小数据量
 * try (var b = SonicExcel.write(os, Foo.class).sheet("数据")) { b.doWrite(list); }
 *
 * // C 分批追加，复用现有分页查询
 * try (var b = SonicExcel.write(os, Foo.class).sheet("数据")) {
 *     while (hasNext) { b.append(page.getList()); }
 * }
 * }</pre>
 *
 * <p><b>不会关闭传入的 OutputStream</b>（默认 autoCloseStream=false）—— Web 导出时
 * {@code response.getOutputStream()} 的生命周期归容器管，我们不能替它做主。
 *
 * @Date 2026-08-08
 */
public final class SonicSheetBuilder<T> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SonicSheetBuilder.class);

    private final OutputStream out;
    private final SheetMeta meta;
    private final Workbook workbook;

    private String sheetName = "Sheet1";
    private int flushEvery = 1000;
    private int maxRowsPerSheet = 1_000_000;
    private boolean freezeHeader = true;
    private boolean escapeFormula = false;
    private boolean autoCloseStream = false;
    private SonicErrorPolicy errorPolicy = SonicErrorPolicy.FAIL_FAST;

    private SheetRoller roller;
    private CellWriter cellWriter;
    private RowConverter<T> rowConverter;
    private boolean started;
    private boolean closed;

    public SonicSheetBuilder(OutputStream out, Class<T> head) {
        this.out = out;
        this.meta = MetaResolver.resolve(head);
        this.workbook = new Workbook(out, "SmartAdmin", "3.0");
    }

    // ------------------------------------------------------------------ 配置

    public SonicSheetBuilder<T> sheet(String name) {
        ensureNotStarted();
        this.sheetName = name;
        return this;
    }

    /**
     * 每写满多少行刷一次盘。这是"防 OOM"的实际开关，不要设成 0 或超大值。
     */
    public SonicSheetBuilder<T> flushEvery(int rows) {
        ensureNotStarted();
        if (rows <= 0) {
            throw new IllegalArgumentException("flushEvery 必须为正数");
        }
        this.flushEvery = rows;
        return this;
    }

    /**
     * 单表数据行上限，超出自动换 Sheet。xlsx 的硬上限是 1,048,576（含表头）。
     */
    public SonicSheetBuilder<T> maxRowsPerSheet(int rows) {
        ensureNotStarted();
        if (rows <= 0 || rows > 1_048_575) {
            throw new IllegalArgumentException("maxRowsPerSheet 必须在 1..1048575 之间");
        }
        this.maxRowsPerSheet = rows;
        return this;
    }

    public SonicSheetBuilder<T> freezeHeader(boolean on) {
        ensureNotStarted();
        this.freezeHeader = on;
        return this;
    }

    /**
     * 给 {@code = + - @} 开头的文本加 {@code '} 前缀。
     *
     * <p><b>默认关闭</b>，这是深思后的选择：本框架所有文本都写成 inlineStr，
     * Excel 对文本型单元格根本不做公式求值，注入面在 xlsx 内几乎不存在；
     * 而默认开启会把 {@code +8613800000000} 这类合法手机号改成 {@code '+8613800000000}，
     * 属于确定发生的数据污染换取几乎不存在的收益。
     * 真要防"用户另存为 CSV 再打开"的场景时，显式打开它。
     */
    public SonicSheetBuilder<T> escapeFormula(boolean on) {
        ensureNotStarted();
        this.escapeFormula = on;
        return this;
    }

    /**
     * 是否在 close() 时关闭传入的 OutputStream。默认 false。
     */
    public SonicSheetBuilder<T> autoCloseStream(boolean on) {
        this.autoCloseStream = on;
        return this;
    }

    /**
     * 脏数据策略。导出默认 FailFast —— 报表少了几行等于事故。
     */
    public SonicSheetBuilder<T> onError(SonicErrorPolicy policy) {
        this.errorPolicy = policy == null ? SonicErrorPolicy.FAIL_FAST : policy;
        return this;
    }

    // ------------------------------------------------------------------ 写入

    public void doWrite(Collection<? extends T> data) {
        append(data);
        close();
    }

    public void doWrite(Stream<? extends T> data) {
        ensureOpen();
        ensureStarted();
        try (Stream<? extends T> s = data) {
            s.forEach(this::writeRow);
        }
        close();
    }

    public SonicSheetBuilder<T> append(Collection<? extends T> data) {
        ensureOpen();
        ensureStarted();
        if (data != null) {
            for (T row : data) {
                writeRow(row);
            }
        }
        return this;
    }

    // ------------------------------------------------------------------ 结果

    public List<SonicRowError> errors() {
        return rowConverter == null ? List.of() : rowConverter.errors();
    }

    public long skippedRows() {
        return rowConverter == null ? 0 : rowConverter.skippedRows();
    }

    public long writtenRows() {
        return roller == null ? 0 : roller.totalDataRows();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            // 空数据也要产出一个带表头的合法文件：fastexcel 的 finish() 遇到零 worksheet 会直接抛
            ensureStarted();
            roller.finish();
            workbook.finish();
            logSummary();
        } catch (IOException e) {
            throw new UncheckedIOException("SonicExcel 导出写盘失败", e);
        } finally {
            if (autoCloseStream) {
                closeQuietly();
            }
        }
    }

    // ------------------------------------------------------------------

    private void writeRow(T row) {
        try {
            int r = roller.prepareRow();
            Object[] values = rowConverter.convert(row, r);
            if (values == null) {
                return;
            }
            Worksheet ws = roller.sheet();
            List<ColumnMeta> columns = meta.columns();
            for (int c = 0; c < columns.size(); c++) {
                cellWriter.write(ws, r, c, values[c], columns.get(c));
            }
            roller.commitRow(values);
        } catch (IOException e) {
            throw new UncheckedIOException("SonicExcel 导出写盘失败", e);
        }
    }

    private void ensureStarted() {
        if (started) {
            return;
        }
        started = true;
        this.cellWriter = new CellWriter(escapeFormula);
        this.rowConverter = new RowConverter<>(meta, errorPolicy);
        this.roller = new SheetRoller(workbook, meta, sheetName, maxRowsPerSheet, flushEvery, freezeHeader);
    }

    private void ensureNotStarted() {
        if (started) {
            throw new IllegalStateException("SonicExcel 已经开始写入，不能再改配置");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SonicExcel 已关闭，不能继续写入");
        }
    }

    private void logSummary() {
        if (rowConverter.skippedRows() > 0 || cellWriter.truncatedCount() > 0) {
            log.warn("[SonicExcel] 导出 {} 完成：写入 {} 行，跳过 {} 行，截断 {} 个超长单元格",
                    meta.type().getSimpleName(), roller.totalDataRows(),
                    rowConverter.skippedRows(), cellWriter.truncatedCount());
        }
    }

    private void closeQuietly() {
        try {
            out.close();
        } catch (IOException e) {
            log.debug("[SonicExcel] 关闭输出流失败", e);
        }
    }
}
