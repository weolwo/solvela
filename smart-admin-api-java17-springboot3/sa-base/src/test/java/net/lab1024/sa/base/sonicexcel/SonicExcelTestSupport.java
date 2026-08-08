package net.lab1024.sa.base.sonicexcel;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 测试用的回读工具。
 *
 * <p><b>关于"自己写自己读"</b>：第③档摘掉 POI 之后，回读只能用 fastexcel-reader。
 * 它和 writer 虽然同属 dhatim，但是两个独立的 artifact、两套独立实现，交叉验证仍然有意义；
 * 真正靠"独立第三方"把关的那一层，由
 * {@link SonicExcelMigrationSemanticsTest} 的固定快照承担 —— 那份期望值是摘掉 POI 之前
 * 用 cn.idev.excel 逐格比对过的。
 *
 * <p>另外凡是结构性的断言（比如共享字符串表必须为空）一律直接读 zip 里的 XML 原文，
 * 不经过任何解析库，见 {@link #rawPart}。
 *
 * @Date 2026-08-08
 */
final class SonicExcelTestSupport {

    private SonicExcelTestSupport() {
    }

    /**
     * 把 xlsx 读成「sheet 名 → 行 → 单元格文本」。
     *
     * <p>取的是单元格<b>存储值</b>的文本形态（数值列会带上原始小数位），
     * 不是 Excel 按格式渲染后的显示值。
     */
    static Map<String, List<List<String>>> read(byte[] xlsx) {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet sheet : workbook.getSheets().toList()) {
                List<List<String>> rows = new ArrayList<>();
                for (Row row : sheet.read()) {
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < row.getCellCount(); c++) {
                        cells.add(row.getCellText(c));
                    }
                    rows.add(cells);
                }
                result.put(sheet.getName(), rows);
            }
        } catch (IOException e) {
            throw new IllegalStateException("回读 xlsx 失败", e);
        }
        return result;
    }

    static List<List<String>> readFirstSheet(byte[] xlsx) {
        return read(xlsx).values().iterator().next();
    }

    /**
     * 取 zip 内某个部件的原文，不经过任何 Excel 解析库。
     *
     * <p>走 ZipFile（读中央目录）而不是 ZipInputStream：opczip 是流式写入的，
     * local header 里的 size 是 0、真实长度在 data descriptor 里，顺序读会抛
     * {@code invalid entry size}。
     */
    static String rawPart(byte[] xlsx, String entryName) {
        try {
            Path tmp = Files.createTempFile("sonic-test-", ".xlsx");
            try {
                Files.write(tmp, xlsx);
                try (ZipFile zip = new ZipFile(tmp.toFile())) {
                    ZipEntry entry = zip.getEntry(entryName);
                    if (entry == null) {
                        return null;
                    }
                    try (var in = zip.getInputStream(entry)) {
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 zip 部件失败：" + entryName, e);
        }
    }
}
