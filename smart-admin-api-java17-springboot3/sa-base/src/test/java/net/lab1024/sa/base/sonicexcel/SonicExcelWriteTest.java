package net.lab1024.sa.base.sonicexcel;

import net.lab1024.sa.base.sonicexcel.annotation.SonicTitle;
import net.lab1024.sa.base.sonicexcel.converter.SonicContext;
import net.lab1024.sa.base.sonicexcel.converter.SonicConverter;
import net.lab1024.sa.base.sonicexcel.error.SonicErrorPolicy;
import net.lab1024.sa.base.sonicexcel.write.SonicSheetBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SonicExcel 写引擎的行为固化测试。
 *
 * @Date 2026-08-08
 */
public class SonicExcelWriteTest {

    // ------------------------------------------------------------------ 内存红线

    @Test
    public void 所有文本走inlineString绝不进sharedStrings() {
        // 🔴 这是整个框架的内存红线。fastexcel 的 Workbook 没有关闭 shared strings 的开关，
        // StringCache 是个永不清理的 HashMap —— 只要有一处漏用了 value(r,c,String)，
        // 千万级高基数文本就会把堆吃穿。这条测试就是那个漏用的哨兵。
        List<Simple> rows = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            rows.add(new Simple("订单号-" + i, BigDecimal.valueOf(i)));
        }
        byte[] xlsx = write(Simple.class, rows, b -> b.sheet("数据"));

        String sst = SonicExcelTestSupport.rawPart(xlsx, "xl/sharedStrings.xml");
        assertNotNull(sst, "sharedStrings.xml 部件本身总是存在（fastexcel 无条件写出），这里检查的是它必须是空的");
        assertTrue(sst.contains("uniqueCount=\"0\""), "共享字符串表必须为空，实际：" + sst.substring(0, Math.min(200, sst.length())));
        assertFalse(sst.contains("<si>"), "共享字符串表里不该有任何条目");

        // 数据本身要真的写出去了，别把"没写"误判成"没进 sharedStrings"
        List<List<String>> sheet = SonicExcelTestSupport.readFirstSheet(xlsx);
        assertEquals(5001, sheet.size());
        assertEquals("订单号-4999", sheet.get(5000).get(0));
    }

    // ------------------------------------------------------------------ 类型路由

    @Test
    public void 各类型的落地形态() {
        Typed row = new Typed();
        row.text = "文本";
        row.integer = 42;
        row.decimal = new BigDecimal("99.99");
        row.flag = Boolean.TRUE;
        row.date = LocalDate.of(2026, 8, 8);
        row.dateTime = LocalDateTime.of(2026, 8, 8, 12, 30, 0);
        row.status = Status.ON;
        row.nothing = null;
        row.blank = "";

        List<List<String>> sheet = SonicExcelTestSupport.readFirstSheet(write(Typed.class, List.of(row), b -> b));
        List<String> data = sheet.get(1);

        assertAll(
                () -> assertEquals("文本", data.get(0)),
                () -> assertEquals("42", data.get(1)),
                () -> assertEquals("99.99", data.get(2)),
                () -> assertEquals("TRUE", data.get(3)),
                () -> assertEquals("ON", data.get(6), "没挂转换器时枚举兜底写 name()"),
                // 后三列是 null / 空串 / null：一个单元格都不写。
                // Excel 里"单元格不存在"和"单元格是空的"渲染完全一样，但千万行导出时能省下可观的 XML 体积
                () -> assertEquals(7, data.size(), "尾部的 null / 空串不该产生占位单元格"));
    }

    @Test
    public void 超长纯数字转文本以免变成科学计数() {
        // 雪花 ID / 订单号 / 身份证：写成数值时 Excel 显示 1.38E+18，而且超过 15 位必然失真
        Typed row = new Typed();
        row.text = "x";
        row.bigId = 1234567890123456789L;

        List<String> data = SonicExcelTestSupport.readFirstSheet(write(Typed.class, List.of(row), b -> b)).get(1);
        assertEquals("1234567890123456789", data.get(9));
    }

    @Test
    public void 十五位以内的数字仍然是数值() {
        Typed row = new Typed();
        row.text = "x";
        row.bigId = 123456789012345L;

        String raw = SonicExcelTestSupport.rawPart(write(Typed.class, List.of(row), b -> b), "xl/worksheets/sheet1.xml");
        assertTrue(raw.contains("<v>123456789012345</v>"), "15 位以内要保持数值形态，便于在 Excel 里参与计算：" + raw);
        assertFalse(raw.contains("<t>123456789012345</t>"), "不该被降级成文本");
    }

    @Test
    public void 超长单元格截断到excel上限() {
        Simple row = new Simple("A".repeat(40000), BigDecimal.ONE);
        List<String> data = SonicExcelTestSupport.readFirstSheet(write(Simple.class, List.of(row), b -> b)).get(1);
        assertEquals(32767, data.get(0).length(), "超过 32767 会产出一个 Excel 打不开的文件，必须截断");
    }

    // ------------------------------------------------------------------ 公式转义

    @Test
    public void 公式转义默认关闭以免污染手机号() {
        // +8613800000000 是合法手机号。默认开转义会把它改成 '+8613800000000 —— 确定发生的数据污染，
        // 换取的是几乎不存在的收益：本框架所有文本都是 inlineStr，Excel 对文本单元格不做公式求值。
        Simple row = new Simple("+8613800000000", BigDecimal.ONE);
        assertEquals("+8613800000000",
                SonicExcelTestSupport.readFirstSheet(write(Simple.class, List.of(row), b -> b)).get(1).get(0));
    }

    @Test
    public void 显式打开转义时加单引号前缀() {
        Simple row = new Simple("=1+1", BigDecimal.ONE);
        assertEquals("'=1+1",
                SonicExcelTestSupport.readFirstSheet(write(Simple.class, List.of(row), b -> b.escapeFormula(true)))
                        .get(1).get(0));
    }

    // ------------------------------------------------------------------ 滚 Sheet

    @Test
    public void 超出单表上限自动换sheet且每张都重写表头() {
        // xlsx 单表硬上限 1,048,576 行，不换 sheet 的话"千万级导出"根本不成立
        List<Simple> rows = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            rows.add(new Simple("第" + i + "条", BigDecimal.valueOf(i)));
        }
        byte[] xlsx = write(Simple.class, rows, b -> b.sheet("数据").maxRowsPerSheet(3));

        Map<String, List<List<String>>> sheets = SonicExcelTestSupport.read(xlsx);
        assertEquals(List.of("数据", "数据_2", "数据_3"), List.copyOf(sheets.keySet()));
        assertEquals(4, sheets.get("数据").size(), "表头 + 3 行");
        assertEquals(4, sheets.get("数据_2").size());
        assertEquals(2, sheets.get("数据_3").size(), "表头 + 剩下的 1 行");
        assertEquals("名称", sheets.get("数据_3").get(0).get(0), "每张新表都要重写表头");
        assertEquals("第7条", sheets.get("数据_3").get(1).get(0));
    }

    // ------------------------------------------------------------------ 边界

    @Test
    public void 空数据也要产出带表头的合法文件() {
        // fastexcel 的 finish() 遇到零 worksheet 会直接抛，不能让"没数据"变成"500"
        byte[] xlsx = write(Simple.class, List.of(), b -> b.sheet("空表"));
        Map<String, List<List<String>>> sheets = SonicExcelTestSupport.read(xlsx);
        assertEquals(1, sheets.size());
        assertEquals(List.of("名称", "金额"), sheets.get("空表").get(0));
        assertEquals(1, sheets.get("空表").size());
    }

    @Test
    public void record是一等公民() {
        // EasyExcel 读侧靠无参构造 + setter 注入，record 根本用不了；这里必须能跑
        byte[] xlsx = write(RecordRow.class, List.of(new RecordRow("张三", 30)), b -> b);
        List<List<String>> sheet = SonicExcelTestSupport.readFirstSheet(xlsx);
        assertEquals(List.of("姓名", "年龄"), sheet.get(0));
        assertEquals(List.of("张三", "30"), sheet.get(1));
    }

    @Test
    public void 关闭是幂等的() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        SonicSheetBuilder<Simple> builder = SonicExcel.write(os, Simple.class);
        builder.append(List.of(new Simple("a", BigDecimal.ONE)));
        builder.close();
        builder.close();
        assertTrue(os.size() > 0);
    }

    // ------------------------------------------------------------------ 错误策略

    @Test
    public void FailFast把行号和列名带进异常() {
        Exploding row = new Exploding("boom");
        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> write(Exploding.class, List.of(row), b -> b));
        // 第一条数据在 Excel 里是第 2 行（第 1 行是表头），报给用户的必须是他肉眼能对上的那个行号
        assertTrue(e.getMessage().contains("第 2 行"), "异常要能定位到行：" + e.getMessage());
        assertTrue(e.getMessage().contains("会炸的列"), "异常要能定位到列：" + e.getMessage());
    }

    @Test
    public void Collect收集错误并跳过整行不留空行() {
        // 跳过必须是"整行不写"而不是"写一半"——所以转换要先算完整行再落笔
        List<Exploding> rows = List.of(new Exploding("ok1"), new Exploding("boom"), new Exploding("ok2"));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] xlsx;
        List<String> errorTexts;
        try (SonicSheetBuilder<Exploding> builder =
                     SonicExcel.write(os, Exploding.class).onError(new SonicErrorPolicy.Collect(10))) {
            builder.append(rows);
            errorTexts = builder.errors().stream().map(err -> err.describe()).toList();
        }
        xlsx = os.toByteArray();

        List<List<String>> sheet = SonicExcelTestSupport.readFirstSheet(xlsx);
        assertEquals(3, sheet.size(), "表头 + 2 行好数据，坏行不占位");
        assertEquals("ok1", sheet.get(1).get(0));
        assertEquals("ok2", sheet.get(2).get(0));
        assertEquals(1, errorTexts.size());
        assertTrue(errorTexts.getFirst().contains("会炸的列"));
    }

    @Test
    public void Collect超过上限熔断() {
        // 千万行全脏时，一行一条日志能把磁盘写满，必须有上限
        List<Exploding> rows = List.of(new Exploding("boom"), new Exploding("boom"), new Exploding("boom"));
        SonicExcelException e = assertThrows(SonicExcelException.class, () -> {
            try (SonicSheetBuilder<Exploding> builder =
                         SonicExcel.write(new ByteArrayOutputStream(), Exploding.class)
                                 .onError(new SonicErrorPolicy.Collect(2))) {
                builder.append(rows);
            }
        });
        assertTrue(e.getMessage().contains("熔断"), e.getMessage());
    }

    // ------------------------------------------------------------------

    private static <T> byte[] write(Class<T> head, List<? extends T> rows,
                                    java.util.function.UnaryOperator<SonicSheetBuilder<T>> config) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (SonicSheetBuilder<T> builder = config.apply(SonicExcel.write(os, head))) {
            builder.append(rows);
        }
        return os.toByteArray();
    }

    // ------------------------------------------------------------------ 夹具

    public static class Simple {
        @SonicTitle("名称")
        private String name;
        @SonicTitle("金额")
        private BigDecimal amount;

        public Simple() {
        }

        public Simple(String name, BigDecimal amount) {
            this.name = name;
            this.amount = amount;
        }

        public String getName() {
            return name;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    public enum Status {
        ON, OFF
    }

    public static class Typed {
        @SonicTitle("文本")
        String text;
        @SonicTitle("整数")
        Integer integer;
        @SonicTitle("小数")
        BigDecimal decimal;
        @SonicTitle("布尔")
        Boolean flag;
        @SonicTitle("日期")
        LocalDate date;
        @SonicTitle("日期时间")
        LocalDateTime dateTime;
        @SonicTitle("枚举")
        Status status;
        @SonicTitle("空值")
        String nothing;
        @SonicTitle("空串")
        String blank;
        @SonicTitle("长整数")
        Long bigId;
    }

    public record RecordRow(@SonicTitle("姓名") String name, @SonicTitle("年龄") Integer age) {
    }

    public static class Exploding {
        @SonicTitle(value = "会炸的列", converter = BoomConverter.class)
        String value;

        public Exploding() {
        }

        public Exploding(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class BoomConverter implements SonicConverter<String, String> {
        @Override
        public String exportConvert(String value, SonicContext ctx) {
            if ("boom".equals(value)) {
                throw new IllegalStateException("模拟字典翻译失败");
            }
            return value;
        }

        @Override
        public String importConvert(String value, SonicContext ctx) {
            return value;
        }
    }
}
