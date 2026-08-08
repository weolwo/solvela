package net.lab1024.sa.base.sonicexcel.write;

import net.lab1024.sa.base.sonicexcel.error.SonicErrorPolicy;
import net.lab1024.sa.base.sonicexcel.error.SonicRowError;
import net.lab1024.sa.base.sonicexcel.meta.ColumnMeta;
import net.lab1024.sa.base.sonicexcel.meta.MetaResolver;
import net.lab1024.sa.base.sonicexcel.meta.SheetMeta;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * CSV 导出通道。
 *
 * <p><b>什么时候该用它</b>：真·千万级。xlsx 光 Deflate 压缩就是分钟级，生成的文件 Excel 打开
 * 还要几十秒，而且单表 1,048,576 行的硬上限逼着你滚 Sheet。CSV 没有行数上限、几乎不耗 CPU，
 * 代价是没有样式、没有多 sheet、数字全是文本。
 *
 * <p>复用 {@link SheetMeta} 与转换器，所以同一个 DTO 既能导 xlsx 也能导 CSV，注解一份。
 *
 * @Date 2026-08-08
 */
public final class SonicCsvWriter<T> implements AutoCloseable {

    /**
     * UTF-8 BOM。<b>不写这三个字节，Excel 打开中文 CSV 就是乱码</b> —— CSV 导出被投诉最多的一件事。
     */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final OutputStream out;
    private final SheetMeta meta;

    private char separator = ',';
    private boolean withBom = true;
    private boolean autoCloseStream = false;
    private SonicErrorPolicy errorPolicy = SonicErrorPolicy.FAIL_FAST;

    private Writer writer;
    private RowConverter<T> rowConverter;
    private long writtenRows;
    private boolean started;
    private boolean closed;

    public SonicCsvWriter(OutputStream out, Class<T> head) {
        this.out = out;
        this.meta = MetaResolver.resolve(head);
    }

    // ------------------------------------------------------------------ 配置

    public SonicCsvWriter<T> separator(char separator) {
        ensureNotStarted();
        this.separator = separator;
        return this;
    }

    /**
     * 是否写 UTF-8 BOM。默认写 —— 只有确认下游是程序而不是 Excel 时才该关掉。
     */
    public SonicCsvWriter<T> withBom(boolean on) {
        ensureNotStarted();
        this.withBom = on;
        return this;
    }

    public SonicCsvWriter<T> autoCloseStream(boolean on) {
        this.autoCloseStream = on;
        return this;
    }

    public SonicCsvWriter<T> onError(SonicErrorPolicy policy) {
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

    public SonicCsvWriter<T> append(Collection<? extends T> data) {
        ensureOpen();
        ensureStarted();
        if (data != null) {
            for (T row : data) {
                writeRow(row);
            }
        }
        return this;
    }

    public List<SonicRowError> errors() {
        return rowConverter == null ? List.of() : rowConverter.errors();
    }

    public long writtenRows() {
        return writtenRows;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            ensureStarted();
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("SonicExcel CSV 写盘失败", e);
        } finally {
            if (autoCloseStream) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // 关流失败不该盖掉业务异常
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private void writeRow(T row) {
        // 行号从 1 起，和 xlsx 侧对齐（0 是表头），错误信息里的行号才能跟 xlsx 说的是同一回事
        Object[] values = rowConverter.convert(row, (int) Math.min(writtenRows + 1, Integer.MAX_VALUE));
        if (values == null) {
            return;
        }
        try {
            for (int c = 0; c < values.length; c++) {
                if (c > 0) {
                    writer.write(separator);
                }
                writer.write(escape(render(values[c])));
            }
            writer.write("\r\n");
            writtenRows++;
        } catch (IOException e) {
            throw new UncheckedIOException("SonicExcel CSV 写盘失败", e);
        }
    }

    /**
     * BigDecimal 用 toPlainString，否则大数会写成科学计数，下游再读就错了。
     */
    private static String render(Object value) {
        return switch (value) {
            case null -> "";
            case BigDecimal d -> d.toPlainString();
            case Temporal t -> t.toString();
            default -> String.valueOf(value);
        };
    }

    /**
     * RFC 4180：含分隔符、引号、换行的字段整体加引号，内部引号翻倍。
     */
    private String escape(String value) {
        boolean needQuote = value.indexOf(separator) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private void ensureStarted() {
        if (started) {
            return;
        }
        started = true;
        try {
            if (withBom) {
                out.write(UTF8_BOM);
            }
            this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 1 << 16);
            this.rowConverter = new RowConverter<>(meta, errorPolicy);
            List<ColumnMeta> columns = meta.columns();
            for (int c = 0; c < columns.size(); c++) {
                if (c > 0) {
                    writer.write(separator);
                }
                writer.write(escape(columns.get(c).title()));
            }
            writer.write("\r\n");
        } catch (IOException e) {
            throw new UncheckedIOException("SonicExcel CSV 写盘失败", e);
        }
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
}
