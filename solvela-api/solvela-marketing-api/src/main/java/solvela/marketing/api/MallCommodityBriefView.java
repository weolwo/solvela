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
 * @param coverUrl       封面图的可直接访问 URL，没有图时为 null。
 *                       🔴 <b>是 URL 不是 file_id</b> —— C 端没有按 id 换 URL 的接口
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
        String coverUrl,
        MallPayTypeEnum payType,
        Integer pointsPrice,
        BigDecimal cashPrice,
        BigDecimal originalPrice,
        boolean favorite,
        Integer availableStock,
        /**
         * 兑换需要的最低等级，{@code 0} = 不限。
         *
         * <p>⚠️ 端上<b>不要</b>拿它和用户等级自己比 —— 比较结果已经算好在
         * {@link #gradeLocked} 里了。自己比的话，保级缓冲期那种
         * 「他在白金但成长值够不着白金」的情况会被算错，
         * 而端上根本拿不到判断这件事需要的数据。
         */
        Integer minGrade,
        /** 专享商品的等级名，如「白金会员」。不限时为 null —— 端上用它拼「XX会员专享」 */
        String minGradeName,
        /**
         * 这个人现在兑不兑得了。{@code true} = 看得见但换不了。
         *
         * <p>🔴 这是<b>服务端算好的结论</b>，不是给端上参考的。下单会再判一次 ——
         * 界面只负责说清楚，拦是服务端的事。
         */
        boolean gradeLocked) {
}
