package net.lab1024.sa.admin.excel;

import net.lab1024.sa.admin.module.business.goods.domain.vo.GoodsExcelVO;
import net.lab1024.sa.admin.module.business.oa.enterprise.domain.vo.EnterpriseExcelVO;
import net.lab1024.sa.base.common.util.SmartExcelUtil;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用<b>项目里真实的两个导出 VO</b> 跑一遍全链路。
 *
 * <p>和 sa-base 里的框架测试不重复：那边的夹具是手写字段（走 MethodHandle 兜底路径），
 * 这两个 VO 是 Lombok {@code @Data} / {@code @Builder}，走的是 LambdaMetafactory 快路径。
 * 两条路径都得有人验。
 *
 * @Date 2026-08-08
 */
public class SonicExcelRealVoTest {

    @Test
    public void 商品导出VO() {
        List<GoodsExcelVO> data = List.of(
                GoodsExcelVO.builder()
                        .categoryName("数码").goodsName("机械键盘").goodsStatus("在售")
                        .place("广东,江苏").price(new BigDecimal("499.00")).remark("带背光").build(),
                GoodsExcelVO.builder()
                        .categoryName("家居").goodsName("台灯").goodsStatus("售罄")
                        .place("浙江").price(new BigDecimal("89.90")).remark(null).build());

        List<List<String>> rows = read(SmartExcelUtil.toBytes("商品", GoodsExcelVO.class, data));

        assertEquals(List.of("商品分类", "商品名称", "商品状态错误", "产地", "商品价格", "备注"), rows.get(0),
                "表头文本与列序必须和迁移前一字不差 —— 用户手里的旧模板还要能用");
        assertEquals(List.of("数码", "机械键盘", "在售", "广东,江苏", "499", "带背光"), rows.get(1));
        assertEquals(3, rows.size());
    }

    @Test
    public void 企业导出VO_原水印接口现在走普通导出() {
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
        // 统一社会信用代码是 18 位字母数字混排，本来就是文本，不该被当成数字处理
        assertEquals("91330000MA2XXXXX1A", rows.get(1).get(1));
        // 11 位手机号在 15 位精度内，写成数值也不会失真，但它是字符串字段，应原样保留
        assertEquals("13800000000", rows.get(1).get(4));
    }

    @Test
    public void 大批量导出不会把共享字符串表撑起来() {
        List<GoodsExcelVO> data = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            data.add(GoodsExcelVO.builder()
                    .categoryName("类目" + i).goodsName("商品" + i).goodsStatus("在售")
                    .place("产地" + i).price(BigDecimal.valueOf(i)).remark("备注" + i).build());
        }
        byte[] xlsx = SmartExcelUtil.toBytes("商品", GoodsExcelVO.class, data);

        // 10 万个唯一字符串，如果走了 value(r,c,String) 就会全部堆在 StringCache 里
        assertTrue(new String(sharedStrings(xlsx)).contains("uniqueCount=\"0\""),
                "共享字符串表必须为空");
        assertEquals(20001, read(xlsx).size());
    }

    // ------------------------------------------------------------------

    private static List<List<String>> read(byte[] xlsx) {
        DataFormatter formatter = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                for (int c = 0; row != null && c < row.getLastCellNum(); c++) {
                    cells.add(row.getCell(c) == null ? "" : formatter.formatCellValue(row.getCell(c)));
                }
                rows.add(cells);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }

    /**
     * 必须落临时文件走 ZipFile（读中央目录）。
     * opczip 是流式写入的，local header 里的 size 是 0、真实长度在 data descriptor 里，
     * 用 ZipInputStream 顺序读会抛 {@code invalid entry size (expected 0 but got N bytes)}。
     */
    private static byte[] sharedStrings(byte[] xlsx) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("sonic-realvo-", ".xlsx");
            try {
                java.nio.file.Files.write(tmp, xlsx);
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tmp.toFile())) {
                    java.util.zip.ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
                    if (entry == null) {
                        throw new IllegalStateException("没找到 sharedStrings.xml");
                    }
                    try (var in = zip.getInputStream(entry)) {
                        return in.readAllBytes();
                    }
                }
            } finally {
                java.nio.file.Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
