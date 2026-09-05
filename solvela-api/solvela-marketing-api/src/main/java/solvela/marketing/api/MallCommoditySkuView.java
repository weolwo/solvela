package solvela.marketing.api;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 一个 SKU。
 *
 * @param skuAttrs       规格组合，如 {@code {颜色: "午夜蓝", 尺码: "38"}}。
 *                       ⚠️ <b>无规格商品也有一行 SKU，这里是空 map</b>（DDL 明写）。
 *                       所以「有没有规格可选」看这个 map 空不空，不是看 SKU 列表长不长。
 *                       <p>规格<b>分组</b>（有哪几组、每组哪些值）不由接口给 ——
 *                       表里就只有这份 JSON，由端上从 SKU 列表推出来。
 * @param pointsPrice    本规格所需积分。<b>「继承商品基准价」已经算好</b>，这里一定有值
 * @param availableStock {@code total_stock - locked_stock - sold_count}，DDL 里是虚拟列
 */
public record MallCommoditySkuView(
        Long skuId,
        String skuCode,
        Map<String, String> skuAttrs,
        Long skuCoverFileId,
        Integer pointsPrice,
        BigDecimal cashPrice,
        Integer availableStock) {
}
