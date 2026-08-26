package solvela.base.sonicexcel;

import solvela.base.sonicexcel.annotation.SonicTitle;
import solvela.base.sonicexcel.error.SonicReadResult;
import solvela.base.sonicexcel.error.SonicRowError;
import solvela.base.sonicexcel.read.SonicSheetReader;
import solvela.base.sonicexcel.write.SonicCsvWriter;
import solvela.base.sonicexcel.write.SonicSheetBuilder;
import solvela.base.sonicexcel.write.SonicTemplateWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * SonicExcel 唯一入口。
 *
 * <p>底层是 {@code org.dhatim:fastexcel}（拉模型 + 流式 zip），不含任何 Apache POI。
 *
 * @Date 2026-08-08
 */
public final class SonicExcel {

    /**
     * {@link #readBytes} 的硬上限。超过这个尺寸必须落盘走 {@link #read(Path, Class)}。
     */
    private static final int MAX_IN_MEMORY_BYTES = 5 * 1024 * 1024;

    private SonicExcel() {
    }

    /**
     * 开启写模式。返回的 builder 是 AutoCloseable，<b>必须 try-with-resources</b> ——
     * xlsx 的 zip 中央目录是 close() 里最后写的，不关等于产出一个打不开的文件。
     */
    public static <T> SonicSheetBuilder<T> write(OutputStream os, Class<T> head) {
        return new SonicSheetBuilder<>(os, head);
    }

    /**
     * 开启读模式。
     *
     * <p><b>入参只接受 {@link Path}，这是刻意的</b>：解析 OOXML 要 zip 随机访问，
     * 把 InputStream 交给 fastexcel-reader 时它会用 {@code SeekableInMemoryByteChannel}
     * 把整个 xlsx 读成堆里的 byte[] —— 100MB 的上传文件在读第一行之前就先吃掉 100MB 连续堆内存。
     * 从 API 层面掐断这种"假流式"误用，比在文档里写一句提醒可靠得多。
     *
     * <p>上传文件怎么落盘、怎么保证删干净，见 {@link solvela.base.sonicexcel.SolvelaExcelUtil}。
     */
    public static <T> SonicSheetReader<T> read(Path file, Class<T> head) {
        return new SonicSheetReader<>(file, head);
    }

    /**
     * 内存逃生口：<b>仅限确定很小的文件</b>（如从对象存储拉下来的模板），硬上限 5MB。
     *
     * <p>刻意只提供一次性读完的重载，不返回 Stream —— 临时文件的生命周期要和流绑定，
     * 徒增一处可能泄漏的地方，而这条路径本来就不该用于大文件。
     */
    public static <T> SonicReadResult<T> readBytes(byte[] content, Class<T> head) {
        if (content.length > MAX_IN_MEMORY_BYTES) {
            throw new SonicExcelException("readBytes 仅支持 5MB 以内的文件，当前 "
                    + content.length / 1024 / 1024 + "MB，请落盘后用 read(Path, Class)");
        }
        Path tmp = null;
        try {
            tmp = SonicTempFiles.create();
            Files.write(tmp, content);
            return read(tmp, head).doReadAll();
        } catch (IOException e) {
            throw new UncheckedIOException("写入临时文件失败", e);
        } finally {
            deleteQuietly(tmp);
        }
    }

    /**
     * CSV 导出通道。真·千万级用它 —— xlsx 光压缩就是分钟级，还有单表 1,048,576 行的硬上限。
     */
    public static <T> SonicCsvWriter<T> writeCsv(OutputStream os, Class<T> head) {
        return new SonicCsvWriter<>(os, head);
    }

    /**
     * 生成导入模板：表头 + 可选示例行 + 下拉校验（列上标了 {@code @SonicOptions} 才有）。
     */
    public static <T> SonicTemplateWriter<T> writeTemplate(OutputStream os, Class<T> head) {
        return new SonicTemplateWriter<>(os, head);
    }

    /**
     * 把行级错误清单导成 xlsx 回给用户。
     *
     * <p>这是导入体验的最后一环：500 行里 30 行有问题，与其在页面上堆一段截断的文字，
     * 不如给一个能直接打开、逐条对照修改的文件。
     */
    public static void writeErrorReport(OutputStream os, List<SonicRowError> errors) {
        List<ErrorRow> rows = errors.stream()
                .map(e -> new ErrorRow(e.rowIndex() + 1, e.title(), e.rawValue(), e.message()))
                .toList();
        try (SonicSheetBuilder<ErrorRow> builder = write(os, ErrorRow.class).sheet("错误明细")) {
            builder.append(rows);
        }
    }

    /**
     * 错误报告的一行。行号已经换算成 Excel 里肉眼可见的编号。
     */
    public record ErrorRow(
            @SonicTitle("行号") Integer rowNumber,
            @SonicTitle("列名") String title,
            @SonicTitle("原始值") String rawValue,
            @SonicTitle("错误说明") String message) {
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 删不掉交给启动扫描兜底
        }
    }
}
