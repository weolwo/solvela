package net.lab1024.sa.base.sonicexcel.read;

import net.lab1024.sa.base.sonicexcel.SonicExcelException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * 打开工作簿之前的入口体检：格式判定 + zip 炸弹防护。
 *
 * <p>摘掉 POI 之后，"什么破文件都能读"这层保护没了，只能靠这里把不该进解析器的东西挡在门外，
 * 并且给出<b>用户能看懂、客服能照着回复</b>的错误信息。
 *
 * @Date 2026-08-08
 */
final class WorkbookGuard {

    /**
     * zip 本地文件头 {@code PK\003\004}。
     */
    private static final byte[] MAGIC_ZIP = {0x50, 0x4B, 0x03, 0x04};

    /**
     * OLE2 复合文档头，即 BIFF97 的 .xls。
     */
    private static final byte[] MAGIC_OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    /**
     * 解压后总字节上限。
     */
    private static final long MAX_UNCOMPRESSED_BYTES = 200L * 1024 * 1024;

    /**
     * zip 条目数上限。正常 xlsx 十几个，多 sheet + 图片也很难过百。
     */
    private static final int MAX_ENTRIES = 1000;

    /**
     * 整体压缩比上限。zip 炸弹的典型特征就是这个值大得离谱。
     */
    private static final long MAX_COMPRESSION_RATIO = 200;

    private WorkbookGuard() {
    }

    static void check(Path file) {
        checkMagic(file);
        checkZipBomb(file);
    }

    /**
     * <b>不能靠文件扩展名判断</b> —— 用户把 .xls 改名成 .xlsx 是常规操作，
     * 不拦的话抛出来的会是一个不知所云的 zip/StAX 异常。
     */
    private static void checkMagic(Path file) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new SonicExcelException("读取文件失败", e);
        }
        if (read >= MAGIC_OLE2.length && startsWith(head, MAGIC_OLE2)) {
            throw new SonicExcelException("检测到旧版 .xls 格式，请用 Excel 另存为 .xlsx 后重新上传");
        }
        if (read < MAGIC_ZIP.length || !startsWith(head, MAGIC_ZIP)) {
            throw new SonicExcelException("文件不是有效的 Excel 文件（.xlsx）");
        }
    }

    private static void checkZipBomb(Path file) {
        long uncompressed = 0;
        long compressed = 0;
        int entries = 0;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry entry = it.nextElement();
                if (++entries > MAX_ENTRIES) {
                    throw new SonicExcelException("Excel 文件内部条目过多（> " + MAX_ENTRIES + "），已拒绝解析");
                }
                // 流式写出的 zip 中央目录里 size 可能是 -1，这种条目只能放过，交给解析器按流处理
                if (entry.getSize() > 0) {
                    uncompressed += entry.getSize();
                }
                if (entry.getCompressedSize() > 0) {
                    compressed += entry.getCompressedSize();
                }
                if (uncompressed > MAX_UNCOMPRESSED_BYTES) {
                    throw new SonicExcelException("Excel 文件解压后过大（> "
                            + (MAX_UNCOMPRESSED_BYTES / 1024 / 1024) + "MB），已拒绝解析");
                }
            }
        } catch (ZipException e) {
            throw new SonicExcelException("Excel 文件已损坏，无法解析", e);
        } catch (IOException e) {
            throw new SonicExcelException("读取 Excel 文件失败", e);
        }
        if (compressed > 0 && uncompressed / compressed > MAX_COMPRESSION_RATIO) {
            throw new SonicExcelException("Excel 文件压缩比异常（" + (uncompressed / compressed)
                    + ":1），疑似压缩炸弹，已拒绝解析");
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
