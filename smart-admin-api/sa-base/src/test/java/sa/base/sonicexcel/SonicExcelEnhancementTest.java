package sa.base.sonicexcel;

import sa.base.common.util.SmartExcelUtil;
import sa.base.sonicexcel.annotation.SonicOptions;
import sa.base.sonicexcel.annotation.SonicTitle;
import sa.base.sonicexcel.converter.SonicContext;
import sa.base.sonicexcel.error.SonicRowError;
import sa.base.sonicexcel.option.SonicOptionProvider;
import sa.base.sonicexcel.write.SonicCsvWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第④档增强项：列宽自适应、CSV 通道、错误报告、导入模板下拉。
 *
 * @Date 2026-08-08
 */
public class SonicExcelEnhancementTest {

    // ------------------------------------------------------------------ 列宽

    @Test
    public void 列宽按数据内容撑开而不是只看表头() {
        // <cols> 是首次 flush 时一次性写出去的，宽度必须赶在那之前落定
        byte[] xlsx = SmartExcelUtil.toBytes("数据", Widths.class,
                List.of(new Widths("短", "这是一段明显比表头长得多的中文内容")));

        String cols = colsXml(xlsx);
        double narrow = widthOf(cols, 0);
        double wide = widthOf(cols, 1);
        assertTrue(wide > narrow, "内容长的列要更宽：" + cols);
        // 表头「长内容列」4 个中文 = 8 宽；数据 17 个中文 = 34 宽，必须是被数据撑开的
        assertTrue(wide > 20, "宽度应由数据决定而不是表头，实际 " + wide);
    }

    @Test
    public void 显式指定的列宽不被数据覆盖() {
        byte[] xlsx = SmartExcelUtil.toBytes("数据", FixedWidth.class,
                List.of(new FixedWidth("非常非常非常非常非常长的一段内容")));
        assertEquals(12.0, widthOf(colsXml(xlsx), 0), 0.01);
    }

    @Test
    public void 行数不足一次采样的小表也要落上列宽() {
        // 这条走的是 finish() 里那次强制落宽 —— 采样没满、也没触发过 flush
        byte[] xlsx = SmartExcelUtil.toBytes("数据", Widths.class, List.of(new Widths("a", "b")));
        assertTrue(colsXml(xlsx).contains("<col"), "小表同样要有 <cols>：" + colsXml(xlsx));
    }

    // ------------------------------------------------------------------ CSV

    @Test
    public void CSV带BOM否则Excel打开中文是乱码() {
        byte[] csv = csv(List.of(new Widths("甲", "乙")));
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
    }

    @Test
    public void CSV按RFC4180转义分隔符引号与换行() {
        byte[] csv = csv(List.of(new Widths("含,逗号", "含\"引号\"和\n换行")));
        String text = new String(csv, StandardCharsets.UTF_8).substring(1);

        assertEquals("短列,长内容列\r\n", text.substring(0, text.indexOf("\r\n") + 2));
        assertTrue(text.contains("\"含,逗号\""), "含分隔符要整体加引号：" + text);
        assertTrue(text.contains("\"\"引号\"\""), "内部引号要翻倍：" + text);
    }

    @Test
    public void CSV把BigDecimal写成普通小数而不是科学计数() {
        // 下游程序再读这份 CSV 时，1E+19 是解析不回原值的
        String text = new String(csv(List.of(new Amount(new BigDecimal("12345678901234567890.50")))),
                StandardCharsets.UTF_8);
        assertTrue(text.contains("12345678901234567890.50"), text);
        assertFalse(text.contains("E+"), text);
    }

    @Test
    public void CSV没有行数上限也不换sheet() {
        List<Widths> rows = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            rows.add(new Widths("a" + i, "b" + i));
        }
        String text = new String(csv(rows), StandardCharsets.UTF_8);
        assertEquals(5001, text.split("\r\n").length, "表头 + 5000 行");
    }

    // ------------------------------------------------------------------ 错误报告

    @Test
    public void 错误清单能导成可直接打开的xlsx() {
        List<SonicRowError> errors = List.of(
                new SonicRowError(2, "商品价格", "abc", "不是合法数字"),
                new SonicRowError(7, "商品状态", "在售中", "不是合法的取值"));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        SonicExcel.writeErrorReport(os, errors);

        List<List<String>> rows = SonicExcelTestSupport.readFirstSheet(os.toByteArray());
        assertEquals(List.of("行号", "列名", "原始值", "错误说明"), rows.get(0));
        // 行号要换算成用户在 Excel 里肉眼看到的编号
        assertEquals(List.of("3", "商品价格", "abc", "不是合法数字"), rows.get(1));
        assertEquals("8", rows.get(2).get(0));
    }

    // ------------------------------------------------------------------ 模板

    @Test
    public void 模板带表头示例行和下拉() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        SonicExcel.writeTemplate(os, Template.class).sheet("导入模板").sample("张三", "在售").write();
        byte[] xlsx = os.toByteArray();

        List<List<String>> rows = SonicExcelTestSupport.readFirstSheet(xlsx);
        assertEquals(List.of("名称", "状态"), rows.get(0));
        assertEquals(List.of("张三", "在售"), rows.get(1), "示例行让用户一眼看出每列该填什么");

        String sheet = SonicExcelTestSupport.rawPart(xlsx, "xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("<dataValidation"), "有 @SonicOptions 的列要有数据有效性：" + sheet);
        assertTrue(sheet.contains("&quot;在售,售罄&quot;") || sheet.contains("\"在售,售罄\""),
                "下拉选项要写进 formula1：" + sheet);
        assertTrue(sheet.contains("sqref=\"B2:B5001\""), "下拉要覆盖后续行供用户下拉填充：" + sheet);
    }

    @Test
    public void 没有SonicOptions的列不加下拉() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        SonicExcel.writeTemplate(os, Widths.class).write();
        String sheet = SonicExcelTestSupport.rawPart(os.toByteArray(), "xl/worksheets/sheet1.xml");
        assertFalse(sheet.contains("<dataValidation"), sheet);
    }

    @Test
    public void 选项过长时跳过下拉而不是产出损坏文件() {
        // Excel 对内联下拉的 formula1 有 255 字符上限，超了整个文件会被判定损坏
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        SonicExcel.writeTemplate(os, TooManyOptions.class).write();
        byte[] xlsx = os.toByteArray();

        String sheet = SonicExcelTestSupport.rawPart(xlsx, "xl/worksheets/sheet1.xml");
        assertFalse(sheet.contains("<dataValidation"), "超长选项应被跳过");
        // 文件本身仍然是好的
        assertEquals(List.of("很多选项"), SonicExcelTestSupport.readFirstSheet(xlsx).get(0));
    }

    // ------------------------------------------------------------------

    private static <T> byte[] csv(List<T> rows) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        @SuppressWarnings("unchecked")
        Class<T> head = (Class<T>) rows.getFirst().getClass();
        try (SonicCsvWriter<T> writer = SonicExcel.writeCsv(os, head)) {
            writer.append(rows);
        }
        return os.toByteArray();
    }

    private static String colsXml(byte[] xlsx) {
        String sheet = SonicExcelTestSupport.rawPart(xlsx, "xl/worksheets/sheet1.xml");
        int start = sheet.indexOf("<cols>");
        return start < 0 ? "" : sheet.substring(start, sheet.indexOf("</cols>") + 7);
    }

    private static double widthOf(String colsXml, int column) {
        String needle = "min=\"" + (column + 1) + "\"";
        int at = colsXml.indexOf(needle);
        if (at < 0) {
            throw new AssertionError("没找到第 " + column + " 列的宽度：" + colsXml);
        }
        int widthAt = colsXml.indexOf("width=\"", at) + 7;
        return Double.parseDouble(colsXml.substring(widthAt, colsXml.indexOf('"', widthAt)));
    }

    // ------------------------------------------------------------------ 夹具

    public record Widths(@SonicTitle("短列") String small, @SonicTitle("长内容列") String large) {
    }

    public record FixedWidth(@SonicTitle(value = "定宽列", width = 12) String value) {
    }

    public record Amount(@SonicTitle("金额") BigDecimal amount) {
    }

    public record Template(
            @SonicTitle("名称") String name,
            @SonicTitle("状态") @SonicOptions({"在售", "售罄"}) String status) {
    }

    public record TooManyOptions(
            @SonicTitle("很多选项") @SonicOptions(provider = LongOptions.class) String value) {
    }

    public static final class LongOptions implements SonicOptionProvider {
        @Override
        public List<String> options(SonicContext ctx) {
            List<String> options = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                options.add("选项" + i);
            }
            return options;
        }
    }
}
