package solvela.marketing.api;

import solvela.enums.MallPayTypeEnum;

import java.math.BigDecimal;

/**
 * 商品列表项。
 *
 * <h3>🔴 积分是整数，现金才是小数</h3>
 * {@code points_price int} vs {@code cash_price decimal(10,2)} —— 两者不是一类东西。
 * 混为一谈的代价是「45000.00 积分」这种展示，或者更糟：拿 Decimal 去算一个整数。
 *
 * @param commodityType  PHYSICAL / COUPON / BALANCE，对齐 PrizeTypeEnum。
 *                       <b>实物要寄，兑换时必须选收货地址</b>
 * @param payType        只有 POINTS(1) 与 POINTS_CASH(2)，<b>没有纯现金商品</b>
 * @param originalPrice  划线原价。🔴 <b>这是「值多少钱」，不是「原来要多少积分」</b> ——
 *                       DDL 列注释原文「仅前端展示『价值￥199』，纯积分商品可留 0」
 * @param favorite       当前会员有没有收藏。<b>未登录时恒 false</b>
 * @param availableStock 各 SKU 可用库存之和。0 表示整个商品已兑完
 */
public record MallCommodityBriefView(
        Long commodityId,
        String commodityCode,
        Long categoryId,
        String commodityType,
        String commodityName,
        String commodityIntro,
        Long coverFileId,
        MallPayTypeEnum payType,
        Integer pointsPrice,
        BigDecimal cashPrice,
        BigDecimal originalPrice,
        boolean favorite,
        Integer availableStock) {
}
