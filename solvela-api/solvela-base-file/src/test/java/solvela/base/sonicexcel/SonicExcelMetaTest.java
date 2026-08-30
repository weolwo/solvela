package solvela.base.sonicexcel;

import solvela.base.sonicexcel.annotation.SonicTitle;
import solvela.base.sonicexcel.meta.MetaResolver;
import solvela.base.sonicexcel.meta.RowConstructor;
import solvela.base.sonicexcel.meta.SheetMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 元数据解析的规则固化。这些规则都是"宁可启动就报错，也不要运行期静默错位"。
 *
 * @Date 2026-08-08
 */
public class SonicExcelMetaTest {

    @Test
    public void 不写index时按声明顺序() {
        List<String> titles = MetaResolver.resolve(Declaration.class).columns().stream()
                .map(c -> c.title()).toList();
        assertEquals(List.of("甲", "乙", "丙"), titles);
    }

    @Test
    public void 全写index时按index排() {
        List<String> titles = MetaResolver.resolve(FullIndex.class).columns().stream()
                .map(c -> c.title()).toList();
        assertEquals(List.of("丙", "甲", "乙"), titles);
    }

    @Test
    public void index只写一半直接报错() {
        // 半写半不写是最容易出错位事故的写法：一半按声明序、一半按显式序，结果无法预期
        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> MetaResolver.resolve(HalfIndex.class));
        assertTrue(e.getMessage().contains("只写了一部分"), e.getMessage());
    }

    @Test
    public void index有空洞直接报错() {
        // 允许空洞的话表头行会出现空单元格，而空标题在导入侧无法寻址
        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> MetaResolver.resolve(GapIndex.class));
        assertTrue(e.getMessage().contains("连续序列"), e.getMessage());
    }

    @Test
    public void index重复直接报错() {
        assertThrows(SonicExcelException.class, () -> MetaResolver.resolve(DupIndex.class));
    }

    @Test
    public void 表头重复直接报错() {
        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> MetaResolver.resolve(DupTitle.class));
        assertTrue(e.getMessage().contains("表头重复"), e.getMessage());
    }

    @Test
    public void 没有任何注解字段直接报错() {
        assertThrows(SonicExcelException.class, () -> MetaResolver.resolve(NoTitle.class));
    }

    @Test
    public void 严格模式下基本类型字段直接报错() {
        // 导入缺列时 int 被静默赋 0（"库存是 0"）而不是 null（"这列没填"），业务语义完全不同。
        // dev/test 下升级成异常，让建模问题在 CI 阶段就红，只打 warn 是没人看的。
        boolean origin = SonicExcelSettings.isStrictMeta();
        try {
            SonicExcelSettings.setStrictMeta(true);
            SonicExcelException e = assertThrows(SonicExcelException.class,
                    () -> MetaResolver.resolve(PrimitiveStrict.class));
            assertTrue(e.getMessage().contains("基本类型"), e.getMessage());
            assertTrue(e.getMessage().contains("count(int)"), "要点名是哪个字段：" + e.getMessage());
        } finally {
            SonicExcelSettings.setStrictMeta(origin);
        }
    }

    @Test
    public void 非严格模式下基本类型只告警不拦截() {
        boolean origin = SonicExcelSettings.isStrictMeta();
        try {
            SonicExcelSettings.setStrictMeta(false);
            assertEquals(1, MetaResolver.resolve(PrimitiveLenient.class).columnCount());
        } finally {
            SonicExcelSettings.setStrictMeta(origin);
        }
    }

    @Test
    public void record解析出canonical构造器且没有setter() {
        SheetMeta meta = MetaResolver.resolve(RecordDto.class);
        RowConstructor.RecordCanonical ctor =
                assertInstanceOf(RowConstructor.RecordCanonical.class, meta.constructor());
        assertEquals(2, ctor.componentCount());
        assertNull(meta.columns().getFirst().setter(), "record 不走 setter 注入");
        assertNotNull(meta.columns().getFirst().getter());
    }

    @Test
    public void 只用于导出的类没有无参构造也不该报错() {
        // 只有 @Builder + @AllArgsConstructor 的导出 VO 很常见，不该连导出都跑不起来；
        // 该在真正导入时才失败
        SheetMeta meta = MetaResolver.resolve(ExportOnly.class);
        assertInstanceOf(RowConstructor.Unavailable.class, meta.constructor());
        assertNotNull(meta.columns().getFirst().getter(), "导出用的取值器必须正常");
    }

    @Test
    public void alias被解析进元数据() {
        assertEquals(List.of("旧名", "更旧的名"), MetaResolver.resolve(Aliased.class).columns().getFirst().alias());
    }

    // ------------------------------------------------------------------ 夹具

    static class Declaration {
        @SonicTitle("甲")
        String a;
        @SonicTitle("乙")
        String b;
        @SonicTitle("丙")
        String c;
    }

    static class FullIndex {
        @SonicTitle(value = "甲", index = 1)
        String a;
        @SonicTitle(value = "乙", index = 2)
        String b;
        @SonicTitle(value = "丙", index = 0)
        String c;
    }

    static class HalfIndex {
        @SonicTitle(value = "甲", index = 0)
        String a;
        @SonicTitle("乙")
        String b;
    }

    static class GapIndex {
        @SonicTitle(value = "甲", index = 0)
        String a;
        @SonicTitle(value = "乙", index = 2)
        String b;
    }

    static class DupIndex {
        @SonicTitle(value = "甲", index = 0)
        String a;
        @SonicTitle(value = "乙", index = 0)
        String b;
    }

    static class DupTitle {
        @SonicTitle("同名")
        String a;
        @SonicTitle("同名")
        String b;
    }

    static class NoTitle {
        String a;
    }

    static class PrimitiveStrict {
        @SonicTitle("数量")
        int count;
    }

    static class PrimitiveLenient {
        @SonicTitle("数量")
        int count;
    }

    record RecordDto(@SonicTitle("姓名") String name, @SonicTitle("年龄") Integer age) {
    }

    static class ExportOnly {
        @SonicTitle("值")
        private final String value;

        ExportOnly(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    static class Aliased {
        @SonicTitle(value = "新名", alias = {"旧名", "更旧的名"})
        String a;
    }
}
