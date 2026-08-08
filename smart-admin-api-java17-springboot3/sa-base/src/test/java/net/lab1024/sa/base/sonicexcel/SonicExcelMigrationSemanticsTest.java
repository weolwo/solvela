package net.lab1024.sa.base.sonicexcel;

import cn.idev.excel.FastExcel;
import cn.idev.excel.annotation.ExcelProperty;
import net.lab1024.sa.base.common.util.SmartExcelUtil;
import net.lab1024.sa.base.sonicexcel.annotation.SonicTitle;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 迁移语义固化：同一份数据，旧的 cn.idev.excel 和新的 SonicExcel 各导一次，<b>逐单元格比对</b>。
 *
 * <p>夹具刻意照抄 {@code GoodsExcelVO} 的形状（5 个 String + 1 个 BigDecimal），
 * 因为项目里真实迁移的两个 VO 就是这个形状 —— 比对的是真实迁移面，不是造出来的场景。
 *
 * <p>第③档摘掉 cn.idev.excel 之后，这个类会退化成"对一份固定期望快照"，
 * 那时候它的价值就从"迁移前后一致"变成"以后别改坏了"。
 *
 * @Date 2026-08-08
 */
public class SonicExcelMigrationSemanticsTest {

    @Test
    public void 旧库与SonicExcel逐格一致() {
        List<Goods> data = sample();

        byte[] legacy = writeWithLegacy(data);
        byte[] sonic = SmartExcelUtil.toBytes("商品", Goods.class, data);

        List<List<String>> expected = SonicExcelTestSupport.readFirstSheet(legacy);
        List<List<String>> actual = SonicExcelTestSupport.readFirstSheet(sonic);

        assertEquals(expected.size(), actual.size(), "行数不一致");
        int width = expected.getFirst().size();
        for (int r = 0; r < expected.size(); r++) {
            assertEquals(pad(expected.get(r), width), pad(actual.get(r), width), "第 " + r + " 行不一致");
        }
    }

    /**
     * 补齐到表头宽度再比。
     *
     * <p>唯一一处已知且刻意保留的差异：值为 null / 空串时 SonicExcel <b>不写这个单元格</b>，
     * 而旧库会写一个空单元格占位。在 Excel 里"单元格不存在"和"单元格是空的"渲染完全一样，
     * 但前者在千万行导出时能省下可观的 XML 体积，所以不打算跟旧库对齐。
     */
    private static List<String> pad(List<String> row, int width) {
        List<String> padded = new ArrayList<>(row);
        while (padded.size() < width) {
            padded.add("");
        }
        return padded;
    }

    @Test
    public void 表头文本与列序与旧库一致() {
        List<List<String>> legacy = SonicExcelTestSupport.readFirstSheet(writeWithLegacy(sample()));
        List<List<String>> sonic = SonicExcelTestSupport.readFirstSheet(
                SmartExcelUtil.toBytes("商品", Goods.class, sample()));
        assertEquals(legacy.getFirst(), sonic.getFirst());
        assertEquals(List.of("商品分类", "商品名称", "商品状态", "产地", "商品价格", "备注"), sonic.getFirst());
    }

    private static List<Goods> sample() {
        List<Goods> list = new ArrayList<>();
        list.add(new Goods("数码", "机械键盘", "在售", "广东,江苏", new BigDecimal("499.00"), "带背光"));
        list.add(new Goods("家居", "台灯", "售罄", "浙江", new BigDecimal("89.90"), null));
        // 中文、空值、空串、小数位、逗号拼接的多值字典 —— 迁移时最容易出偏差的几种
        list.add(new Goods("食品", "坚果礼盒", "在售", "", new BigDecimal("0.01"), ""));
        return list;
    }

    private static byte[] writeWithLegacy(List<Goods> data) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        FastExcel.write(os, Goods.class).autoCloseStream(Boolean.FALSE).sheet("商品").doWrite(data);
        return os.toByteArray();
    }

    /**
     * 两套注解并存，同一个对象喂给两个引擎。
     */
    public static class Goods {

        @ExcelProperty("商品分类")
        @SonicTitle("商品分类")
        private String categoryName;

        @ExcelProperty("商品名称")
        @SonicTitle("商品名称")
        private String goodsName;

        @ExcelProperty("商品状态")
        @SonicTitle("商品状态")
        private String goodsStatus;

        @ExcelProperty("产地")
        @SonicTitle("产地")
        private String place;

        @ExcelProperty("商品价格")
        @SonicTitle("商品价格")
        private BigDecimal price;

        @ExcelProperty("备注")
        @SonicTitle("备注")
        private String remark;

        public Goods() {
        }

        public Goods(String categoryName, String goodsName, String goodsStatus,
                     String place, BigDecimal price, String remark) {
            this.categoryName = categoryName;
            this.goodsName = goodsName;
            this.goodsStatus = goodsStatus;
            this.place = place;
            this.price = price;
            this.remark = remark;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public String getGoodsName() {
            return goodsName;
        }

        public void setGoodsName(String goodsName) {
            this.goodsName = goodsName;
        }

        public String getGoodsStatus() {
            return goodsStatus;
        }

        public void setGoodsStatus(String goodsStatus) {
            this.goodsStatus = goodsStatus;
        }

        public String getPlace() {
            return place;
        }

        public void setPlace(String place) {
            this.place = place;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
