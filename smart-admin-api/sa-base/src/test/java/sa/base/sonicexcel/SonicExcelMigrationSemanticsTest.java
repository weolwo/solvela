package sa.base.sonicexcel;

import sa.base.common.util.SmartExcelUtil;
import sa.base.sonicexcel.annotation.SonicTitle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 迁移语义固化：把导出结果钉死在一份<b>固定期望快照</b>上。
 *
 * <p><b>这份期望值的来历</b>：第①②档期间，这个类是拿同一份数据分别用
 * {@code cn.idev.excel}（阿里系）和 SonicExcel 各导一次、逐单元格比对的，两边完全一致。
 * 第③档把 cn.idev.excel 摘掉之后没有"另一个引擎"可比了，于是把当时比对通过的结果固化成常量。
 * <b>所以下面每一个字符串都不是随手写的，是和旧库对齐过的。</b>
 *
 * <p>夹具刻意照抄 {@code GoodsExcelVO} 迁移前的形状（5 个 String + 1 个 BigDecimal），
 * 因为项目里真实迁移的两个 VO 就是这个形状。
 *
 * <p>唯一一处已知且刻意保留的差异：值为 null / 空串时 SonicExcel <b>不写这个单元格</b>，
 * 旧库会写一个空单元格占位。Excel 里"单元格不存在"和"单元格是空的"渲染完全一样，
 * 但前者在千万行导出时能省下可观的 XML 体积 —— 所以快照里这些位置就是"短一截"。
 *
 * @Date 2026-08-08
 */
public class SonicExcelMigrationSemanticsTest {

    /**
     * 与 cn.idev.excel 逐格比对通过的导出结果。
     */
    private static final List<List<String>> EXPECTED = List.of(
            List.of("商品分类", "商品名称", "商品状态", "产地", "商品价格", "备注"),
            List.of("数码", "机械键盘", "在售", "广东,江苏", "499.00", "带背光"),
            // remark 为 null：不写单元格，所以这一行只有 5 格
            List.of("家居", "台灯", "售罄", "浙江", "89.90"),
            // place 为空串、remark 为空串：同样不写
            List.of("食品", "坚果礼盒", "在售", "", "0.01"));

    @Test
    public void 导出结果与旧库快照逐格一致() {
        List<List<String>> actual = SonicExcelTestSupport.readFirstSheet(
                SmartExcelUtil.toBytes("商品", Goods.class, sample()));

        assertEquals(EXPECTED.size(), actual.size(), "行数不一致");
        for (int r = 0; r < EXPECTED.size(); r++) {
            assertEquals(EXPECTED.get(r), actual.get(r), "第 " + r + " 行不一致");
        }
    }

    @Test
    public void 表头文本与列序不能漂() {
        // 表头一旦漂了，用户手里所有旧模板全部导入失败 —— 这条要单独钉住
        assertEquals(EXPECTED.getFirst(),
                SonicExcelTestSupport.readFirstSheet(
                        SmartExcelUtil.toBytes("商品", Goods.class, sample())).getFirst());
    }

    private static List<Goods> sample() {
        List<Goods> list = new ArrayList<>();
        list.add(new Goods("数码", "机械键盘", "在售", "广东,江苏", new BigDecimal("499.00"), "带背光"));
        list.add(new Goods("家居", "台灯", "售罄", "浙江", new BigDecimal("89.90"), null));
        // 中文、空值、空串、小数位、逗号拼接的多值字典 —— 迁移时最容易出偏差的几种
        list.add(new Goods("食品", "坚果礼盒", "在售", "", new BigDecimal("0.01"), ""));
        return list;
    }

    public static class Goods {

        @SonicTitle("商品分类")
        private String categoryName;

        @SonicTitle("商品名称")
        private String goodsName;

        @SonicTitle("商品状态")
        private String goodsStatus;

        @SonicTitle("产地")
        private String place;

        @SonicTitle("商品价格")
        private BigDecimal price;

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

        public String getGoodsName() {
            return goodsName;
        }

        public String getGoodsStatus() {
            return goodsStatus;
        }

        public String getPlace() {
            return place;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public String getRemark() {
            return remark;
        }
    }
}
