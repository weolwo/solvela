package solvela.admin.module.mall.sku.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

/**
 * 库存总览查询表单。
 *
 * <p>这个页面回答的是<b>「哪些规格快卖光了」</b> —— 一个在别处答不了的问题：
 * 商品列表只有商品级的库存合计（一个规格断货看不出来），
 * 商品编辑页一次只能看一个商品。
 *
 * <p>它<b>不负责改库存</b>：改库存在商品编辑页，那里有批量设置，
 * 而且改动会连同价格、状态一起走同一个聚合保存事务。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:37:50
 * @Copyright weolwo
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MallSkuQueryForm extends PageParam {

    @Schema(description = "商品名称：模糊匹配")
    private String commodityName;

    @Schema(description = "商品编码：精确匹配")
    private String commodityCode;

    @Schema(description = "SKU编码：精确匹配")
    private String skuCode;

    @Schema(description = "分类id")
    private Long categoryId;

    @Schema(description = "SKU状态：0-停用, 1-启用")
    private Integer skuStatus;

    @Schema(description = "商品状态：0-下架, 1-上架, 2-草稿")
    private Integer commodityStatus;

    /**
     * 只看告警：可用库存 ≤ 阈值。
     *
     * <p>这是这个页面的主用法 —— 打开就想知道「今天该补哪些货」，
     * 而不是从几百个规格里自己找。
     */
    @Schema(description = "只看可用库存 ≤ 该值的规格")
    private Integer stockBelow;
}
