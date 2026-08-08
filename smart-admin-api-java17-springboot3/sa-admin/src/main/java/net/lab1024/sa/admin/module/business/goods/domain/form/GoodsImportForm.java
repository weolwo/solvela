package net.lab1024.sa.admin.module.business.goods.domain.form;

import net.lab1024.sa.base.sonicexcel.annotation.SonicTitle;

import java.math.BigDecimal;

/**
 * 商品 导入表单。
 *
 * <p>做成 record：导入 DTO 本来就该是不可变的，而且<b>全部用包装类型</b> ——
 * 基本类型在缺列时会被静默赋 0 / false（"库存是 0"），和 null（"这一列没填"）在业务上完全不是一回事。
 * MetaResolver 会在 dev/test 下把这种建模直接拦下来。
 *
 * <p>EasyExcel 读侧靠无参构造 + setter 注入，record 用不了；SonicExcel 走 canonical 构造器，
 * 所以这里可以是 record。
 *
 * @Author 1024创新实验室: 胡克
 * @Date 2021-10-25 20:26:54
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
public record GoodsImportForm(

        @SonicTitle("商品分类") String categoryName,

        @SonicTitle("商品名称") String goodsName,

        // 表头这个错别字是历史遗留，用 alias 兜住可能已经被人手工改对的模板
        @SonicTitle(value = "商品状态错误", alias = "商品状态") String goodsStatus,

        @SonicTitle("产地") String place,

        @SonicTitle("商品价格") BigDecimal price,

        @SonicTitle("备注") String remark
) {
}
