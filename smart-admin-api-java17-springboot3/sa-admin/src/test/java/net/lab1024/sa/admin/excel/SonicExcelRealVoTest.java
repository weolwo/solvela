package net.lab1024.sa.admin.excel;

import net.lab1024.sa.admin.module.business.category.service.CategoryQueryService;
import net.lab1024.sa.admin.module.business.goods.constant.GoodsStatusEnum;
import net.lab1024.sa.admin.module.business.goods.domain.form.GoodsImportForm;
import net.lab1024.sa.admin.module.business.goods.domain.vo.GoodsExcelVO;
import net.lab1024.sa.admin.module.business.goods.excel.GoodsCategoryConverter;
import net.lab1024.sa.admin.module.business.oa.enterprise.domain.vo.EnterpriseExcelVO;
import net.lab1024.sa.base.common.util.SmartExcelUtil;
import net.lab1024.sa.base.module.support.dict.excel.SonicDictConverter;
import net.lab1024.sa.base.module.support.dict.service.DictService;
import net.lab1024.sa.base.sonicexcel.SonicExcel;
import net.lab1024.sa.base.sonicexcel.converter.SonicConverterFactory;
import net.lab1024.sa.base.sonicexcel.error.SonicReadResult;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用<b>项目里真实的 VO / Form</b> 跑一遍全链路。
 *
 * <p>和 sa-base 里的框架测试不重复：这里验的是三件框架测试覆盖不到的事 ——
 * 真实 VO 走的是 Lombok getter 的 LambdaMetafactory 快路径；
 * 转换器是从 Spring 容器里取的；表头文本必须和迁移前一字不差。
 *
 * @Date 2026-08-08
 */
public class SonicExcelRealVoTest {

    @TempDir
    Path dir;

    /**
     * 把转换器按"Spring Bean"的方式喂给框架。
     *
     * <p>SonicConverterFactory 是「容器优先、无参构造兜底」的，而 GoodsCategoryConverter /
     * SonicDictConverter 都要注入 service —— 走兜底路径拿到的实例依赖是空的。
     * 这里注册一个只装了桩的 BeanFactory，正好也验证了「转换器可以是 Bean」这条本框架
     * 相对阿里系的核心差异确实生效。
     */
    @BeforeAll
    static void registerConverters() {
        CategoryQueryService categoryQueryService = Mockito.mock(CategoryQueryService.class);
        Mockito.when(categoryQueryService.queryCategoryName(100L)).thenReturn("数码");
        Mockito.when(categoryQueryService.queryCategoryName(200L)).thenReturn("家居");

        DictService dictService = Mockito.mock(DictService.class);
        Mockito.when(dictService.getDictDataLabel("GOODS_PLACE", "GD")).thenReturn("广东");
        Mockito.when(dictService.getDictDataLabel("GOODS_PLACE", "JS")).thenReturn("江苏");
        Mockito.when(dictService.getDictDataLabel("GOODS_PLACE", "ZJ")).thenReturn("浙江");

        GoodsCategoryConverter categoryConverter = new GoodsCategoryConverter();
        ReflectionTestUtils.setField(categoryConverter, "categoryQueryService", categoryQueryService);
        SonicDictConverter dictConverter = new SonicDictConverter();
        ReflectionTestUtils.setField(dictConverter, "dictService", dictService);

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("goodsCategoryConverter", categoryConverter);
        beanFactory.registerSingleton("sonicDictConverter", dictConverter);
        SonicConverterFactory.setBeanFactory(beanFactory);
    }

    // ------------------------------------------------------------------ 导出

    @Test
    public void 商品导出VO_三处翻译全部由转换器完成() {
        // 迁移前这三处翻译是手写在 GoodsService#getAllGoods 里拼 VO 的，
        // 现在 VO 里放的是实体原始值
        List<GoodsExcelVO> data = List.of(
                GoodsExcelVO.builder()
                        .categoryId(100L)
                        .goodsName("机械键盘")
                        .goodsStatus(GoodsStatusEnum.SELL.getValue())
                        .place("GD,JS")
                        .price(new BigDecimal("499.00"))
                        .remark("带背光").build(),
                GoodsExcelVO.builder()
                        .categoryId(200L)
                        .goodsName("台灯")
                        .goodsStatus(GoodsStatusEnum.SELL_OUT.getValue())
                        .place("ZJ")
                        .price(new BigDecimal("89.90"))
                        .remark(null).build());

        List<List<String>> rows = read(SmartExcelUtil.toBytes("商品", GoodsExcelVO.class, data));

        assertEquals(List.of("商品分类", "商品名称", "商品状态错误", "产地", "商品价格", "备注"), rows.get(0),
                "表头文本与列序必须和迁移前一字不差 —— 用户手里的旧模板还要能用");
        // 价格断言的是单元格里的存储值 499.00（BigDecimal 原样写入，小数位保留）。
        // 第③档之前这里是 "499" —— 那是 POI DataFormatter 按 General 格式渲染出来的显示值。
        // 文件内容没变，变的只是回读工具
        assertEquals(List.of("数码", "机械键盘", "售卖中", "广东,江苏", "499.00", "带背光"), rows.get(1));
        assertEquals("售罄", rows.get(2).get(2));
        assertEquals("浙江", rows.get(2).get(3));
        assertEquals(3, rows.size());
    }

    @Test
    public void 企业导出VO() {
        EnterpriseExcelVO vo = new EnterpriseExcelVO();
        vo.setEnterpriseName("一零二四创新实验室");
        vo.setUnifiedSocialCreditCode("91330000MA2XXXXX1A");
        vo.setTypeName("有限责任公司");
        vo.setContact("卓大");
        vo.setContactPhone("13800000000");
        vo.setEmail("lab1024@163.com");
        vo.setProvinceName("浙江省");
        vo.setCityName("杭州市");
        vo.setDistrictName("西湖区");
        vo.setAddress("文三路 100 号");

        List<List<String>> rows = read(SmartExcelUtil.toBytes("企业信息", EnterpriseExcelVO.class, List.of(vo)));

        assertEquals(10, rows.get(0).size());
        assertEquals("企业名称", rows.get(0).get(0));
        assertEquals("一零二四创新实验室", rows.get(1).get(0));
        // 统一社会信用代码是字母数字混排，本来就是文本，不该被当成数字处理
        assertEquals("91330000MA2XXXXX1A", rows.get(1).get(1));
        assertEquals("13800000000", rows.get(1).get(4));
    }

    @Test
    public void 大批量导出不会把共享字符串表撑起来() {
        List<EnterpriseExcelVO> data = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            EnterpriseExcelVO vo = new EnterpriseExcelVO();
            vo.setEnterpriseName("企业" + i);
            vo.setUnifiedSocialCreditCode("CODE" + i);
            vo.setContact("联系人" + i);
            vo.setAddress("地址" + i);
            data.add(vo);
        }
        byte[] xlsx = SmartExcelUtil.toBytes("企业信息", EnterpriseExcelVO.class, data);

        // 8 万个唯一字符串，走了 value(r,c,String) 就会全部堆在 StringCache 里
        assertTrue(new String(sharedStrings(xlsx)).contains("uniqueCount=\"0\""), "共享字符串表必须为空");
        assertEquals(20001, read(xlsx).size());
    }

    // ------------------------------------------------------------------ 导入

    @Test
    public void 商品导入Form是record且列顺序错乱也能读() throws IOException {
        // GoodsImportForm 从 POJO 改成了 record —— EasyExcel 读侧靠 setter 注入，做不到这件事
        Path file = dir.resolve("import.xlsx");
        Files.write(file, rawSheet(List.of(
                // 故意把列顺序打乱，并且用被人手工改对过的表头「商品状态」
                List.of("备注", "商品价格", "产地", "商品状态", "商品名称", "商品分类"),
                List.of("好货", "12.30", "广东", "在售", "键盘", "数码"),
                List.of("", "", "", "", "", ""))));

        SonicReadResult<GoodsImportForm> result = SonicExcel.read(file, GoodsImportForm.class).doReadAll();

        assertFalse(result.hasError(), result.describeErrors(3));
        assertEquals(1, result.data().size(), "尾部空行要被滤掉");
        GoodsImportForm form = result.data().getFirst();
        assertEquals("键盘", form.goodsName());
        assertEquals("数码", form.categoryName());
        assertEquals("在售", form.goodsStatus(), "alias 兜住了被改过字的表头");
        assertEquals(new BigDecimal("12.30"), form.price());
    }

    // ------------------------------------------------------------------

    private static byte[] rawSheet(List<List<String>> rows) throws IOException {
        var buffer = new java.io.ByteArrayOutputStream();
        try (var wb = new org.dhatim.fastexcel.Workbook(buffer, "SonicExcelTest", "1.0")) {
            var sheet = wb.newWorksheet("数据");
            for (int r = 0; r < rows.size(); r++) {
                for (int c = 0; c < rows.get(r).size(); c++) {
                    sheet.inlineString(r, c, rows.get(r).get(c));
                }
            }
            sheet.finish();
        }
        return buffer.toByteArray();
    }

    /**
     * 回读用 fastexcel-reader 而不是 SonicExcel 自己的读引擎 —— 自己写自己读，
     * 两边有同一个理解偏差时会互相掩盖。取的是单元格存储值的文本形态。
     */
    private static List<List<String>> read(byte[] xlsx) {
        List<List<String>> rows = new ArrayList<>();
        try (ReadableWorkbook wb = new ReadableWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getFirstSheet();
            for (Row row : sheet.read()) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getCellCount(); c++) {
                    cells.add(row.getCellText(c));
                }
                rows.add(cells);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }

    /**
     * 必须落临时文件走 ZipFile（读中央目录）。opczip 是流式写入的，local header 里的 size 是 0、
     * 真实长度在 data descriptor 里，用 ZipInputStream 顺序读会抛 invalid entry size。
     */
    private byte[] sharedStrings(byte[] xlsx) {
        try {
            Path tmp = dir.resolve("shared-" + System.nanoTime() + ".xlsx");
            Files.write(tmp, xlsx);
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tmp.toFile())) {
                java.util.zip.ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
                if (entry == null) {
                    throw new IllegalStateException("没找到 sharedStrings.xml");
                }
                try (var in = zip.getInputStream(entry)) {
                    return in.readAllBytes();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
