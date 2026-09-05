package solvela.mall.clientapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.mall.constant.MallConst;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轮播图查的必须是 {@code MALL_COMMODITY_BANNER}，不是 {@code MALL_COMMODITY}。
 *
 * <h3>这条测试对应一个真实的坑</h3>
 * 2026-09-05：商品配了 2 张轮播图，详情页一张都不显示。
 *
 * <p>直接原因是详情里 {@code bannerUrls} 写死了空列表（「等文件引用登记接上后填」，
 * 然后就一直没填）。但接的时候还有第二个坑：<b>mall.sql 里那句
 * 「复用 t_file_relation(biz_type='MALL_COMMODITY')」写漏了后缀</b>。
 * 后台保存时登记的是两组 —— 封面进 {@code MALL_COMMODITY}、
 * 轮播图进 {@code MALL_COMMODITY_BANNER}（见 {@code MallCommoditySaveCommand}）。
 * 照 DDL 注释拿前者去查，查到的是封面自己，而且<b>不报错</b>：
 * 结果是图集里出现一张和主图一模一样的图，看着像功能好了。
 *
 * <p>两个常量只差一个后缀、类型都是 String，编译器帮不上忙。所以钉在这里。
 *
 * @Date 2026-09-05
 */
class BannerBizTypeTest {

    private static final Path FACADE = Path.of(
            "src", "main", "java", "solvela", "mall", "clientapi", "MallClientFacade.java");

    @Test
    @DisplayName("🔴 两个常量不能弄混：BANNER 那个才是轮播图")
    void 两个常量含义不同() {
        assertEquals("MALL_COMMODITY", MallConst.BIZ_TYPE);
        assertEquals("MALL_COMMODITY_BANNER", MallConst.BIZ_TYPE_BANNER);
        // 后缀是唯一的区别 —— 正因为这样才容易写错
        assertTrue(MallConst.BIZ_TYPE_BANNER.startsWith(MallConst.BIZ_TYPE));
    }

    @Test
    @DisplayName("🔴 详情页查轮播图用的是 BIZ_TYPE_BANNER，且没有写死的空列表")
    void 轮播图查对了组() throws IOException {
        String source = Files.readString(FACADE, StandardCharsets.UTF_8);

        assertTrue(source.contains("MallConst.BIZ_TYPE_BANNER"),
                """
                        MallClientFacade 里没有引用 BIZ_TYPE_BANNER。
                        轮播图必须按 MALL_COMMODITY_BANNER 查 —— 用 MALL_COMMODITY 查到的是封面，
                        而且不会报错，表现是图集里出现一张和主图一样的图。
                        """);

        assertFalse(source.contains("listBizFileIds(\n                MallConst.BIZ_TYPE,"),
                "轮播图查成了 BIZ_TYPE（封面那组）");

        /*
         * 这一句是当初的占位。它在的时候详情页 bannerUrls 恒为空，
         * 而所有测试都是绿的 —— 因为没有一条断言说过「这里该有图」。
         */
        assertFalse(source.contains("轮播图走 t_file_relation，等文件引用登记接上后填"),
                "bannerUrls 还是那个写死的空列表占位");
    }

    @Test
    @DisplayName("轮播图按 sort 排 —— 顺序是运营配的，不能按 file_id")
    void 按sort排() throws IOException {
        /*
         * 排序不在这里做：FileRelationDao.listByBiz 自己 orderByAsc(sort)，
         * 那一列的注释原文就是「附件顺序，轮播图必需」。
         * 本断言守的是「不要在 facade 里另排一次」—— 按 file_id 排会让
         * 运营调整顺序完全不生效，而这种不生效没有任何报错。
         */
        String source = Files.readString(FACADE, StandardCharsets.UTF_8);
        int banner = source.indexOf("BIZ_TYPE_BANNER");
        assertTrue(banner > 0, "没找到轮播图那段");
        String after = source.substring(banner, Math.min(source.length(), banner + 1200));
        assertFalse(after.contains("sorted("),
                "facade 里对轮播图又排了一次 —— listByBiz 已经按 sort 排好了");
    }
}
