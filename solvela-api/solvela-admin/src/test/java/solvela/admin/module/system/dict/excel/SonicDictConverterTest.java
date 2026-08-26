package solvela.admin.module.system.dict.excel;

import solvela.base.exception.BusinessException;
import solvela.base.sonicexcel.SolvelaExcelUtil;
import solvela.admin.module.system.dict.service.DictService;
import solvela.base.sonicexcel.SonicExcel;
import solvela.base.sonicexcel.SonicExcelException;
import solvela.base.sonicexcel.annotation.SonicTitle;
import solvela.base.sonicexcel.converter.SonicConverterFactory;
import solvela.base.sonicexcel.error.SonicReadResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典转换器的<b>双向</b>行为。
 *
 * <p>导入方向是本轮补上的：字典配在库里，用户填的是标签（"广东"），库里要存码值（"GD"）。
 *
 * @Date 2026-08-08
 */
public class SonicDictConverterTest {

    private static final String DICT = "GOODS_PLACE";

    @TempDir
    Path dir;

    private static DictService dictService;

    @BeforeAll
    static void wire() {
        dictService = Mockito.mock(DictService.class);
        Mockito.when(dictService.getDictDataLabel(DICT, "GD")).thenReturn("广东");
        Mockito.when(dictService.getDictDataLabel(DICT, "JS")).thenReturn("江苏");
        Mockito.when(dictService.getDictDataValueByLabel(DICT, "广东")).thenReturn("GD");
        Mockito.when(dictService.getDictDataValueByLabel(DICT, "江苏")).thenReturn("JS");

        SonicDictConverter converter = new SonicDictConverter();
        ReflectionTestUtils.setField(converter, "dictService", dictService);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("sonicDictConverter", converter);
        SonicConverterFactory.setBeanFactory(beanFactory);
    }

    @Test
    public void 导出把码值翻译成标签() {
        List<List<String>> rows = readBack(SolvelaExcelUtil.toBytes("数据", Place.class,
                List.of(new Place("GD,JS"))));
        assertEquals("广东,江苏", rows.get(1).getFirst());
    }

    @Test
    public void 导入把标签还原成码值() {
        Path file = write(List.of(List.of("产地"), List.of("广东,江苏")));
        SonicReadResult<Place> result = SonicExcel.read(file, Place.class).doReadAll();

        assertFalse(result.hasError(), result.describeErrors(3));
        assertEquals("GD,JS", result.data().getFirst().getPlace());
    }

    @Test
    public void 导入时不认识的标签变成行级错误而不是脏数据() {
        // 关键：宁可这一行报错，也不能悄悄写个空值或原样把标签塞进库里
        Path file = write(List.of(List.of("产地"), List.of("广东"), List.of("火星"), List.of("江苏")));
        SonicReadResult<Place> result = SonicExcel.read(file, Place.class).doReadAll();

        assertEquals(2, result.data().size(), "好行照常导入");
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().describe().contains("火星"), result.describeErrors(3));
        assertTrue(result.errors().getFirst().describe().contains("第 3 行"), result.describeErrors(3));
    }

    @Test
    public void 同名标签有歧义时报错而不是随便挑一个() {
        // 映射有歧义还硬映射就是往库里写脏数据；这是字典配置错误，必须让人看见
        Mockito.when(dictService.getDictDataValueByLabel(DICT, "重复"))
                .thenThrow(new BusinessException("字典 " + DICT + " 下存在 2 个同名标签「重复」"));

        Path file = write(List.of(List.of("产地"), List.of("重复")));
        SonicReadResult<Place> result = SonicExcel.read(file, Place.class).doReadAll();

        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().describe().contains("同名标签"), result.describeErrors(3));
    }

    @Test
    public void 空值原样透传不去查字典() {
        Path file = write(List.of(List.of("产地"), List.of("")));
        SonicReadResult<Place> result = SonicExcel.read(file, Place.class).doReadAll();
        assertTrue(result.data().isEmpty(), "整行为空会被当成空行滤掉");
        assertFalse(result.hasError());
    }

    @Test
    public void 没标注SonicDict时给出能定位的报错() {
        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SolvelaExcelUtil.toBytes("数据", MissingDict.class, List.of(new MissingDict("x"))));
        assertTrue(e.getMessage().contains("@SonicDict"), e.getMessage());
    }

    // ------------------------------------------------------------------

    private Path write(List<List<String>> rows) {
        Path file = dir.resolve("dict-" + System.nanoTime() + ".xlsx");
        try (var out = Files.newOutputStream(file);
             var wb = new org.dhatim.fastexcel.Workbook(out, "SonicExcelTest", "1.0")) {
            var sheet = wb.newWorksheet("数据");
            for (int r = 0; r < rows.size(); r++) {
                for (int c = 0; c < rows.get(r).size(); c++) {
                    sheet.inlineString(r, c, rows.get(r).get(c));
                }
            }
            sheet.finish();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return file;
    }

    private List<List<String>> readBack(byte[] xlsx) {
        try {
            Path tmp = dir.resolve("back-" + System.nanoTime() + ".xlsx");
            Files.write(tmp, xlsx);
            List<List<String>> rows = new java.util.ArrayList<>();
            try (var wb = new org.dhatim.fastexcel.reader.ReadableWorkbook(tmp.toFile())) {
                for (var row : wb.getFirstSheet().read()) {
                    List<String> cells = new java.util.ArrayList<>();
                    for (int c = 0; c < row.getCellCount(); c++) {
                        cells.add(row.getCellText(c));
                    }
                    rows.add(cells);
                }
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class Place {
        @SonicTitle(value = "产地", converter = SonicDictConverter.class)
        @SonicDict(DICT)
        private String place;

        public Place() {
        }

        public Place(String place) {
            this.place = place;
        }

        public String getPlace() {
            return place;
        }

        public void setPlace(String place) {
            this.place = place;
        }
    }

    public static class MissingDict {
        @SonicTitle(value = "忘了标注", converter = SonicDictConverter.class)
        private String value;

        public MissingDict() {
        }

        public MissingDict(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
