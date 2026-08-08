package net.lab1024.sa.base.sonicexcel;

import net.lab1024.sa.base.sonicexcel.write.SonicSheetBuilder;

import java.io.OutputStream;

/**
 * SonicExcel 唯一入口。
 *
 * <p>底层是 {@code org.dhatim:fastexcel}（拉模型 + 流式 zip），不含任何 Apache POI。
 *
 * @Date 2026-08-08
 */
public final class SonicExcel {

    private SonicExcel() {
    }

    /**
     * 开启写模式。返回的 builder 是 AutoCloseable，<b>必须 try-with-resources</b> ——
     * xlsx 的 zip 中央目录是 close() 里最后写的，不关等于产出一个打不开的文件。
     */
    public static <T> SonicSheetBuilder<T> write(OutputStream os, Class<T> head) {
        return new SonicSheetBuilder<>(os, head);
    }

    // 读模式（SonicExcel.read(Path, Class)）在第②档加入。
    // 提前说明为什么一定是 Path 而不是 InputStream：解析 OOXML 要 zip 随机访问，
    // fastexcel-reader 拿到 InputStream 时会用 SeekableInMemoryByteChannel 把整个文件读进堆，
    // 100MB 的上传文件在读第一行之前就先吃掉 100MB 连续堆内存。
}
