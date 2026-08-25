package solvela.base.sonicexcel;

import solvela.base.common.util.SolvelaExcelUtil;
import solvela.base.sonicexcel.annotation.SonicTitle;
import solvela.base.sonicexcel.error.SonicErrorPolicy;
import solvela.base.sonicexcel.error.SonicReadResult;
import solvela.base.sonicexcel.error.SonicRowError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 读引擎行为固化。
 *
 * <p>用例基本都是"用写引擎造一个 xlsx，再用读引擎读回来"——
 * 这样既验证了读，也顺带验证了读写两侧共用同一份 SheetMeta 是真的对称的。
 *
 * @Date 2026-08-08
 */
public class SonicExcelReadTest {

    @TempDir
    Path dir;

    // ------------------------------------------------------------------ 表头寻址

    @Test
    public void 列顺序错乱也能正确对上() {
        // 用户拿到模板后拖动列顺序是常态，按下标硬读必然错位
        Path file = write(Shuffled.class, List.of(new Shuffled("备注内容", new BigDecimal("12.5"), "张三")));

        SonicReadResult<Person> result = SonicExcel.read(file, Person.class).doReadAll();

        assertFalse(result.hasError(), result.describeErrors(3));
        assertEquals(1, result.data().size());
        Person p = result.data().getFirst();
        assertEquals("张三", p.getName());
        assertEquals(new BigDecimal("12.5"), p.getAmount());
        assertEquals("备注内容", p.getRemark());
    }

    @Test
    public void 多出来的列忽略缺少的列留空() {
        Path file = write(Partial.class, List.of(new Partial("李四", "多余的值")));

        Person p = SonicExcel.read(file, Person.class).doReadAll().data().getFirst();
        assertEquals("李四", p.getName());
        assertNull(p.getAmount(), "文件里没有这一列，留空而不是报错");
        assertNull(p.getRemark());
    }

    @Test
    public void 表头带BOM不间断空格全角空格照样匹配() {
        // 这几个字符恰恰最常出现在用户从网页粘进 Excel 的表头里，而且肉眼完全看不出来
        Path file = writeRaw(List.of(
                List.of("﻿姓名", " 金额 ", "　备注　"),
                List.of("王五", "9.9", "ok")));

        Person p = SonicExcel.read(file, Person.class).doReadAll().data().getFirst();
        assertEquals("王五", p.getName());
        assertEquals(new BigDecimal("9.9"), p.getAmount());
        assertEquals("ok", p.getRemark());
    }

    @Test
    public void alias兜住改过字的历史表头() {
        // 没有 alias 的话，改一次表头，用户手里所有旧模板全部导入失败
        Path file = writeRaw(List.of(
                List.of("姓名", "金额", "备注说明"),
                List.of("赵六", "1", "x")));

        Person p = SonicExcel.read(file, Person.class).doReadAll().data().getFirst();
        assertEquals("x", p.getRemark());
    }

    @Test
    public void 表头一列都对不上时直接报错() {
        Path file = writeRaw(List.of(List.of("甲", "乙", "丙"), List.of("1", "2", "3")));
        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SonicExcel.read(file, Person.class).doReadAll());
        assertTrue(e.getMessage().contains("最新模板"), e.getMessage());
    }

    // ------------------------------------------------------------------ 空行与类型

    @Test
    public void 尾部空行被过滤掉() {
        // Excel 常带成千上万个"看起来是空的"行；不滤掉就会出现"导入了 2 条却提示处理了 5000 行"
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("姓名", "金额", "备注"));
        rows.add(List.of("甲", "1", "a"));
        rows.add(List.of("", "", ""));
        rows.add(List.of("  ", " ", ""));
        rows.add(List.of("乙", "2", "b"));
        Path file = writeRaw(rows);

        SonicSheetReaderProbe probe = readWithProbe(file);
        assertEquals(2, probe.result.data().size());
        assertFalse(probe.result.hasError(), "空行不是错误");
    }

    @Test
    public void 数字和日期被存成文本也能读() {
        // 用户粘贴、老系统导出、WPS 另存都会把数字/日期写成文本单元格
        Path file = writeRaw(List.of(
                List.of("整数", "小数", "日期", "布尔"),
                List.of("42", "1,234.50", "2026-08-08", "是")));

        Typed t = SonicExcel.read(file, Typed.class).doReadAll().data().getFirst();
        assertEquals(42, t.getCount());
        assertEquals(new BigDecimal("1234.50"), t.getAmount());
        assertEquals(LocalDate.of(2026, 8, 8), t.getDay());
        assertTrue(t.getFlag());
    }

    @Test
    public void 写出去再读回来是等价的() {
        Path file = write(Person.class, List.of(
                new Person("甲", new BigDecimal("1.10"), "x"),
                new Person("乙", null, null)));

        List<Person> back = SonicExcel.read(file, Person.class).doReadAll().data();
        assertEquals(2, back.size());
        assertEquals("甲", back.get(0).getName());
        assertEquals(new BigDecimal("1.10"), back.get(0).getAmount());
        assertNull(back.get(1).getAmount(), "写的时候 null 不落单元格，读回来还得是 null");
    }

    @Test
    public void record走canonical构造器() {
        Path file = writeRaw(List.of(List.of("姓名", "年龄"), List.of("张三", "30")));
        PersonRecord r = SonicExcel.read(file, PersonRecord.class).doReadAll().data().getFirst();
        assertEquals(new PersonRecord("张三", 30), r);
    }

    // ------------------------------------------------------------------ 错误模型

    @Test
    public void Collect收集行级错误且带行号列名原值() {
        Path file = writeRaw(List.of(
                List.of("整数", "小数", "日期", "布尔"),
                List.of("1", "1", "2026-01-01", "是"),
                List.of("不是数字", "1", "2026-01-01", "是"),
                List.of("3", "1", "2026-01-01", "是")));

        SonicReadResult<Typed> result = SonicExcel.read(file, Typed.class).doReadAll();

        assertEquals(2, result.data().size(), "坏行被跳过，好行照常");
        assertEquals(1, result.errors().size());
        SonicRowError error = result.errors().getFirst();
        assertEquals("整数", error.title());
        assertEquals("不是数字", error.rawValue(), "原值要带上，不然用户不知道该改什么");
        assertTrue(error.describe().contains("第 3 行"), "行号要和 Excel 里肉眼看到的一致：" + error.describe());
    }

    @Test
    public void FailFast立刻中断() {
        Path file = writeRaw(List.of(
                List.of("整数", "小数", "日期", "布尔"),
                List.of("坏", "1", "2026-01-01", "是")));

        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SonicExcel.read(file, Typed.class).onError(SonicErrorPolicy.FAIL_FAST).doReadAll());
        assertTrue(e.getMessage().contains("第 2 行"), e.getMessage());
    }

    @Test
    public void Collect超过上限熔断() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("整数", "小数", "日期", "布尔"));
        for (int i = 0; i < 10; i++) {
            rows.add(List.of("坏", "1", "2026-01-01", "是"));
        }
        Path file = writeRaw(rows);

        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SonicExcel.read(file, Typed.class).onError(new SonicErrorPolicy.Collect(3)).doReadAll());
        assertTrue(e.getMessage().contains("熔断"), e.getMessage());
    }

    @Test
    public void 行数超上限直接拒绝() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("姓名", "金额", "备注"));
        for (int i = 0; i < 20; i++) {
            rows.add(List.of("x" + i, "1", "a"));
        }
        Path file = writeRaw(rows);

        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SonicExcel.read(file, Person.class).maxRows(5).doReadAll());
        assertTrue(e.getMessage().contains("超过上限"), e.getMessage());
    }

    // ------------------------------------------------------------------ 入口体检

    @Test
    public void 上传xls给出可照抄的提示而不是天书异常() {
        // 用户把 .xls 改名成 .xlsx 是常规操作，所以只能看字节头，不能看扩展名。
        // 这是这类组件最高频的线上工单
        Path file = dir.resolve("fake.xlsx");
        writeBytes(file, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0, 0, 0});

        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SonicExcel.read(file, Person.class).doReadAll());
        assertTrue(e.getMessage().contains("另存为 .xlsx"), e.getMessage());
    }

    @Test
    public void 合法zip但不是Excel时给出可照抄的提示() {
        // WorkbookGuard 只能按字节头挡掉「根本不是 xlsx」的东西。
        // 非 Excel 工具产出的 xlsx 是合法 zip，挡不住，只会在解析到一半时炸 ——
        // 本项目不承诺兼容这类文件（架构文档 §1.2），但必须让用户知道该怎么办
        Path file = dir.resolve("zip-but-not-xlsx.xlsx");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("hello.txt"));
            zos.write("not an excel".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SonicExcel.read(file, Person.class).doReadAll());
        assertTrue(e.getMessage().contains("另存为 .xlsx"), "要给出用户能照着做的动作：" + e.getMessage());
    }

    @Test
    public void 非Excel文件给出明确错误() {
        Path file = dir.resolve("note.xlsx");
        writeBytes(file, "这其实是一个文本文件".getBytes(StandardCharsets.UTF_8));

        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SonicExcel.read(file, Person.class).doReadAll());
        assertTrue(e.getMessage().contains("不是有效的 Excel"), e.getMessage());
    }

    // ------------------------------------------------------------------ 资源

    @Test
    public void 流关闭后文件句柄被释放() {
        // Windows 上句柄没释放就删不掉文件，这条能同时验证 onClose 确实关了工作簿
        Path file = write(Person.class, List.of(new Person("甲", BigDecimal.ONE, "x")));
        try (Stream<Person> stream = SonicExcel.read(file, Person.class).doRead()) {
            assertEquals(1, stream.count());
        }
        assertTrue(deleteWorks(file), "Stream 关闭后应能删除源文件，否则说明 zip 句柄泄漏了");
    }

    @Test
    public void readBytes拒绝超过5MB的内容() {
        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SonicExcel.readBytes(new byte[6 * 1024 * 1024], Person.class));
        assertTrue(e.getMessage().contains("read(Path"), e.getMessage());
    }

    // ------------------------------------------------------------------ 工具

    private record SonicSheetReaderProbe(SonicReadResult<Person> result) {
    }

    private SonicSheetReaderProbe readWithProbe(Path file) {
        return new SonicSheetReaderProbe(SonicExcel.read(file, Person.class).doReadAll());
    }

    private <T> Path write(Class<T> head, List<? extends T> rows) {
        Path file = dir.resolve("data-" + System.nanoTime() + ".xlsx");
        writeBytes(file, SolvelaExcelUtil.toBytes("数据", head, rows));
        return file;
    }

    /**
     * 直接铺一张"表头 + 数据"的字符串表格，用来造那些实体类表达不了的形态
     * （表头带隐形字符、列顺序诡异、单元格是文本形态的数字……）。
     */
    private Path writeRaw(List<List<String>> rows) {
        Path file = dir.resolve("raw-" + System.nanoTime() + ".xlsx");
        try (var out = Files.newOutputStream(file);
             var wb = new org.dhatim.fastexcel.Workbook(out, "SonicExcelTest", "1.0")) {
            var sheet = wb.newWorksheet("数据");
            for (int r = 0; r < rows.size(); r++) {
                List<String> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    sheet.inlineString(r, c, row.get(c));
                }
            }
            sheet.finish();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return file;
    }

    private static void writeBytes(Path file, byte[] content) {
        try {
            Files.write(file, content);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean deleteWorks(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 夹具

    public static class Person {
        @SonicTitle("姓名")
        private String name;
        @SonicTitle("金额")
        private BigDecimal amount;
        @SonicTitle(value = "备注", alias = {"备注说明", "说明"})
        private String remark;

        public Person() {
        }

        public Person(String name, BigDecimal amount, String remark) {
            this.name = name;
            this.amount = amount;
            this.remark = remark;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    /**
     * 和 {@link Person} 同名的三列，但顺序完全打乱。
     */
    public static class Shuffled {
        @SonicTitle("备注")
        private String remark;
        @SonicTitle("金额")
        private BigDecimal amount;
        @SonicTitle("姓名")
        private String name;

        public Shuffled() {
        }

        public Shuffled(String remark, BigDecimal amount, String name) {
            this.remark = remark;
            this.amount = amount;
            this.name = name;
        }

        public String getRemark() {
            return remark;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 只有一列对得上，外加一列 {@link Person} 里没有的。
     */
    public static class Partial {
        @SonicTitle("姓名")
        private String name;
        @SonicTitle("无关列")
        private String extra;

        public Partial() {
        }

        public Partial(String name, String extra) {
            this.name = name;
            this.extra = extra;
        }

        public String getName() {
            return name;
        }

        public String getExtra() {
            return extra;
        }
    }

    public static class Typed {
        @SonicTitle("整数")
        private Integer count;
        @SonicTitle("小数")
        private BigDecimal amount;
        @SonicTitle("日期")
        private LocalDate day;
        @SonicTitle("布尔")
        private Boolean flag;

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public LocalDate getDay() {
            return day;
        }

        public void setDay(LocalDate day) {
            this.day = day;
        }

        public Boolean getFlag() {
            return flag;
        }

        public void setFlag(Boolean flag) {
            this.flag = flag;
        }
    }

    public record PersonRecord(@SonicTitle("姓名") String name, @SonicTitle("年龄") Integer age) {
    }
}
