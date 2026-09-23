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
        /**
         * 这个人要付的积分价，<b>已经含等级折扣</b>。
         *
         * <p>🔴 端上直接显示这个数，<b>不要</b>自己拿 {@link #listPointsPrice} 乘折扣率 ——
         * 取整方向（向下）、商品退出等级折扣、折扣率越界兜底，三件事都在服务端，
         * 端上算出来的数会和实际扣的分对不上。而用户只看得到扣的那个。
         */
        Integer pointsPrice,
        /**
         * 挂牌积分价，<b>不含等级折扣</b>。等于 {@link #pointsPrice} 时说明这个人没享到折扣。
         *
         * <p>端上用途只有一个：比它大就在旁边划一道。
         * ⚠️ 它<b>不是</b> {@link #originalPrice} —— 那个是「值多少钱」（现金），
         * 这个是「原本要多少分」。两者一个划线位放一个，不要混。
         */
        Integer listPointsPrice,
        /**
         * 这个人的积分折扣率，{@code 100} = 没有折扣。端上用它拼「白金 8.8 折」。
         *
         * <p>⚠️ 整个请求里是同一个值，每件商品都带一份是为了让端上不必再传一遍上下文。
         * 未登录、等级 0、这一档没配折扣率，都是 100。
         */
        Integer gradeDiscountPercent,
        BigDecimal cashPrice,
        /**
         * 各在售规格<b>不同价</b>，端上要在对价后面加「起」。
         *
         * <p>🔴 这里发的价是<b>最便宜那个在售规格</b>的价，不是商品基准价 ——
         * 基准价只是 SKU 的继承来源，不保证有人按它卖。
         * 不加「起」的话，一个点进去发现要多付的用户会认为被骗了。
         */
        boolean priceVaries,
        BigDecimal originalPrice,
        boolean favorite,
        Integer availableStock,
        List<String> bannerUrls,
        String detailContent,
        String exchangeNotice,
        String limitPeriod,
        Integer limitCount,
        Integer remainingCount,
        List<MallCommoditySkuView> skus,
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
