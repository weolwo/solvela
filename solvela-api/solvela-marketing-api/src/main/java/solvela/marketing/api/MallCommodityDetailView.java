package solvela.marketing.api;

import solvela.enums.MallPayTypeEnum;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品详情。比列表项多出图文、须知、限兑与 SKU。
 *
 * @param bannerFileIds  轮播图 file_id 列表。来自 {@code t_file_relation}
 *                       （那张表的 sort 列注释原文就是「轮播图必需」，不另造图册表）
 * @param detailContent  图文详情，富文本 HTML。来自运营后台，按可信内容渲染
 * @param exchangeNotice 兑换须知：券的核销说明、实物的发货时效等
 * @param limitPeriod    限兑周期 LIFETIME / DAILY / WEEKLY / MONTHLY
 * @param limitCount     周期内单会员限兑件数。<b>0 = 不限制</b>
 * @param remainingCount 本周期还能兑几件。<b>由服务端用数据库时钟算</b> ——
 *                       端上拿 limitCount 减自己数的次数，会和服务端的 period_key 口径对不上。
 *                       不限制（limitCount=0）或未登录时为 null
 * @param skus           至少一行。无规格商品也有一行，见 {@link MallCommoditySkuView#skuAttrs}
 */
public record MallCommodityDetailView(
        Long commodityId,
        String commodityCode,
        Long categoryId,
        String commodityType,
        String commodityName,
        String commodityIntro,
        String coverUrl,
        MallPayTypeEnum payType,
        Integer pointsPrice,
        BigDecimal cashPrice,
        BigDecimal originalPrice,
        boolean favorite,
        Integer availableStock,
        List<String> bannerUrls,
        String detailContent,
        String exchangeNotice,
        String limitPeriod,
        Integer limitCount,
        Integer remainingCount,
        List<MallCommoditySkuView> skus) {
}
