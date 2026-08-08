package net.lab1024.sa.base.sonicexcel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
 * <p>回读故意用 POI 而不是 SonicExcel 自己 —— 自己写自己读，两边有同一个理解偏差时会互相掩盖。
 * 第③档摘掉 POI 后这里换成第②档的读引擎 + 一份固定的期望快照。
 *
 * @Date 2026-08-08
 */
final class SonicExcelTestSupport {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private SonicExcelTestSupport() {
    }

    /**
     * 把 xlsx 读成「sheet 名 → 行 → 单元格显示文本」。
     * 用 DataFormatter 是为了比较"用户看到的东西"，而不是内部存储形态。
     */
    static Map<String, List<List<String>>> read(byte[] xlsx) {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                List<List<String>> rows = new ArrayList<>();
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    List<String> cells = new ArrayList<>();
                    if (row != null) {
                        for (int c = 0; c < row.getLastCellNum(); c++) {
                            Cell cell = row.getCell(c);
                            cells.add(cell == null ? "" : FORMATTER.formatCellValue(cell));
                        }
                    }
                    rows.add(cells);
                }
                result.put(sheet.getSheetName(), rows);
            }
        } catch (IOException e) {
            throw new IllegalStateException("回读 xlsx 失败", e);
        }
        return result;
    }

    /**
     * 第一个 sheet 的所有行。
     */
    static List<List<String>> readFirstSheet(byte[] xlsx) {
        return read(xlsx).values().iterator().next();
    }

    /**
     * 取 zip 内某个部件的原文。走 ZipFile（读中央目录）而不是 ZipInputStream，
     * 因为 opczip 是流式写入、带 data descriptor 的。
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
