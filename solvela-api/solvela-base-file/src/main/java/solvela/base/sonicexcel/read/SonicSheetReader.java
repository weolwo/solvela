package solvela.base.sonicexcel.read;

import solvela.base.sonicexcel.SonicExcelException;
import solvela.base.sonicexcel.error.SonicErrorPolicy;
import solvela.base.sonicexcel.error.SonicReadResult;
import solvela.base.sonicexcel.error.SonicRowError;
import solvela.base.sonicexcel.meta.MetaResolver;
import solvela.base.sonicexcel.meta.SheetMeta;
import org.dhatim.fastexcel.reader.ExcelReaderException;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 读门面。
 *
 * <pre>{@code
 * // 大文件：惰性流 + Gatherers 分批入库
 * try (Stream<Foo> rows = SonicExcel.read(path, Foo.class).doRead()) {
 *     rows.gather(Gatherers.windowFixed(1000)).forEach(fooService::insertBatch);
 * }
 *
 * // 中小文件：一次读完，顺带拿到行级错误清单
 * SonicReadResult<Foo> result = SonicExcel.read(path, Foo.class).doReadAll();
 * }</pre>
 *
 * <p><b>{@link #doRead()} 返回的 Stream 必须 try-with-resources</b>，
 * close 才会释放底层 zip 句柄。
 *
 * @Date 2026-08-08
 */
public final class SonicSheetReader<T> {

    private static final Logger log = LoggerFactory.getLogger(SonicSheetReader.class);

    private final Path file;
    private final SheetMeta meta;

    private int sheetIndex;
    private String sheetName;
    private int headerRow;
    private SonicErrorPolicy errorPolicy = SonicErrorPolicy.collect();
    private int maxRows = 100_000;

    private final List<SonicRowError> errors = new ArrayList<>();
    private long seenRows;
    private long blankRows;
    private long skippedRows;

    public SonicSheetReader(Path file, Class<T> head) {
        this.file = file;
        this.meta = MetaResolver.resolve(head);
    }

    // ------------------------------------------------------------------ 配置

    public SonicSheetReader<T> sheet(int index) {
        this.sheetIndex = index;
        this.sheetName = null;
        return this;
    }

    public SonicSheetReader<T> sheet(String name) {
        this.sheetName = name;
        return this;
    }

    public SonicSheetReader<T> headerRow(int index) {
        this.headerRow = index;
        return this;
    }

    /**
     * 脏数据策略。导入默认 {@code Collect(200)} —— 用户就是会传脏数据，
     * 一行坏了不该毁掉整批，但也绝不能静默丢掉。
     */
    public SonicSheetReader<T> onError(SonicErrorPolicy policy) {
        this.errorPolicy = policy == null ? SonicErrorPolicy.collect() : policy;
        return this;
    }

    /**
     * 行数上限，防止一个超大文件把服务拖死。
     */
    public SonicSheetReader<T> maxRows(int rows) {
        this.maxRows = rows;
        return this;
    }

    // ------------------------------------------------------------------ 读取

    /**
     * 惰性流。<b>必须 try-with-resources</b>；错误清单要等流被消费完才完整，用 {@link #errors()} 取。
     */
    public Stream<T> doRead() {
        WorkbookGuard.check(file);
        ReadableWorkbook workbook = null;
        Stream<Row> rows = null;
        try {
            workbook = new ReadableWorkbook(file.toFile());
            Sheet sheet = pickSheet(workbook);
            rows = sheet.openStream();
            Iterator<Row> iterator = translating(rows.iterator());
            RowMapper<T> mapper = new RowMapper<>(meta, readHeader(iterator));

            Stream<T> result = StreamSupport
                    .stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                    .map(row -> mapRow(mapper, row))
                    .filter(java.util.Objects::nonNull);
            return closeWith(result, rows, workbook);
        } catch (IOException e) {
            closeQuietly(rows, workbook);
            throw new UncheckedIOException("读取 Excel 失败", e);
        } catch (RuntimeException e) {
            closeQuietly(rows, workbook);
            throw translate(e);
        }
    }

    /**
     * 惰性流是在 try 块之外被消费的，解析异常会绕过上面的 catch —— 在迭代器这一层再兜一次。
     */
    private static Iterator<Row> translating(Iterator<Row> delegate) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                try {
                    return delegate.hasNext();
                } catch (RuntimeException e) {
                    throw translate(e);
                }
            }

            @Override
            public Row next() {
                try {
                    return delegate.next();
                } catch (RuntimeException e) {
                    throw translate(e);
                }
            }
        };
    }

    /**
     * 把底层解析异常翻译成用户能照着做的话。
     *
     * <p>`WorkbookGuard` 只能按字节头挡掉「根本不是 xlsx」的东西；
     * <b>非 Excel 工具（WPS 等）产出的文件是合法 zip，挡不住</b>，只会在解析到一半时炸。
     * 本项目<b>不承诺兼容非 Excel 产出的 xlsx</b>（见架构文档 §1.2），
     * 但至少要让用户知道该怎么办，而不是收到一段 StAX 天书。
     */
    private static RuntimeException translate(RuntimeException e) {
        if (e instanceof SonicExcelException) {
            return e;
        }
        if (e instanceof ExcelReaderException) {
            return new SonicExcelException("Excel 文件解析失败。本系统只支持 Microsoft Excel 生成的标准 .xlsx，"
                    + "如果这个文件来自其他表格软件，请先用 Excel 打开并另存为 .xlsx 后重试", e);
        }
        return e;
    }

    /**
     * 一次性读完。中小数据量（&lt; 10 万行）用这个，能直接拿到「数据 + 错误清单」。
     */
    public SonicReadResult<T> doReadAll() {
        try (Stream<T> stream = doRead()) {
            // 汇总日志由 onClose 统一打，这里不重复
            return new SonicReadResult<>(stream.toList(), List.copyOf(errors));
        }
    }

    public List<SonicRowError> errors() {
        return Collections.unmodifiableList(errors);
    }

    public long blankRows() {
        return blankRows;
    }

    public long skippedRows() {
        return skippedRows;
    }

    // ------------------------------------------------------------------

    private Sheet pickSheet(ReadableWorkbook workbook) {
        if (sheetName != null) {
            Optional<Sheet> found = workbook.findSheet(sheetName);
            return found.orElseThrow(() -> new SonicExcelException("Excel 里没有名为「" + sheetName + "」的工作表"));
        }
        return workbook.getSheet(sheetIndex)
                .orElseThrow(() -> new SonicExcelException("Excel 里没有第 " + (sheetIndex + 1) + " 个工作表"));
    }

    /**
     * 吃掉表头之前的行，用表头行建立列映射。
     */
    private int[] readHeader(Iterator<Row> iterator) {
        Row header = null;
        for (int i = 0; i <= headerRow; i++) {
            if (!iterator.hasNext()) {
                throw new SonicExcelException("Excel 里没有第 " + (headerRow + 1) + " 行表头，文件可能是空的");
            }
            header = iterator.next();
        }
        int[] positions = HeaderMatcher.match(meta, header);
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] < 0) {
                missing.add(meta.columns().get(i).title());
            }
        }
        if (missing.size() == positions.length) {
            throw new SonicExcelException("表头一列都对不上，请确认用的是最新模板。期望的表头："
                    + meta.columns().stream().map(c -> c.title()).toList());
        }
        if (!missing.isEmpty()) {
            log.warn("[SonicExcel] 导入 {} 时缺少列 {}，这些字段会留空", meta.type().getSimpleName(), missing);
        }
        return positions;
    }

    private T mapRow(RowMapper<T> mapper, Row row) {
        if (++seenRows > maxRows) {
            throw new SonicExcelException("Excel 数据行数超过上限 " + maxRows + "，请拆分后再导入");
        }
        // Excel 常带成千上万个"看起来是空的"行（选中过、设过格式），不滤掉就会出现
        // "导入了 3 条却提示处理了 5000 行"
        if (mapper.isBlankRow(row)) {
            blankRows++;
            return null;
        }
        return mapper.map(row, this::onRowError);
    }

    private void onRowError(int rowIndex, String title, String rawValue, Throwable cause) {
        SonicRowError error = new SonicRowError(rowIndex, title, rawValue, describe(cause));
        skippedRows++;
        switch (errorPolicy) {
            case SonicErrorPolicy.FailFast ignored ->
                    throw new SonicExcelException("导入失败，" + error.describe(), cause);
            case SonicErrorPolicy.Collect(int maxErrors) -> {
                errors.add(error);
                if (errors.size() > maxErrors) {
                    throw new SonicExcelException("导入错误数超过上限 " + maxErrors
                            + "，已熔断。首条：" + errors.getFirst().describe(), cause);
                }
            }
            case SonicErrorPolicy.Skip ignored -> {
                // 静默跳过，结束时只打一条汇总，不是每行一条日志
            }
        }
    }

    private static String describe(Throwable cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private void logSummary() {
        if (skippedRows > 0 || blankRows > 0) {
            log.info("[SonicExcel] 导入 {} 完成：跳过 {} 行有问题的数据，忽略 {} 个空行",
                    meta.type().getSimpleName(), skippedRows, blankRows);
        }
    }

    private Stream<T> closeWith(Stream<T> stream, Stream<Row> rows, ReadableWorkbook workbook) {
        return stream.onClose(() -> {
            logSummary();
            closeQuietly(rows, workbook);
        });
    }

    private static void closeQuietly(Stream<Row> rows, ReadableWorkbook workbook) {
        if (rows != null) {
            rows.close();
        }
        if (workbook != null) {
            try {
                workbook.close();
            } catch (IOException e) {
                log.debug("[SonicExcel] 关闭工作簿失败", e);
            }
        }
    }
}
