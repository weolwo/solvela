package solvela.mall.favorite.domain.dto;

import solvela.enums.MallCommodityStatusEnum;
import lombok.Data;

/**
 * 商品收藏排行的一行。
 *
 * <p>收藏是<b>需求信号</b>：想要但还没兑。它和销量说的不是一件事 ——
 * 「收藏很多但兑换很少」通常意味着积分定价偏高或库存长期为 0，
 * 那正是运营该动手调的地方，而销量榜看不出来。所以这一行同时带上兑换数。
 *
 * @Date 2026-08-23
 */
@Data
public class MallFavoriteRankDTO {

    /** 商品id */
    private Long commodityId;

    /** 商品名称 */
    private String commodityName;

    /** 商品编码 */
    private String commodityCode;

    /** 封面图 file_id */
    private Long coverFileId;

    /** 商品状态：0-下架, 1-上架, 2-草稿 */
    private MallCommodityStatusEnum commodityStatus;

    /**
     * <b>最低在售规格</b>所需积分，不是 {@code t_mall_commodity.points_price}。
     *
     * <p>🔴 2026-09-23 订正。商品表上那一列只是 SKU 没填价时的继承来源，
     * 不保证有人按它卖 —— 库里就有基准价 99999 而唯一在售规格只要 1000 的商品，
     * 这一列此前把它显示成真实值的 <b>100 倍</b>。
     *
     * <p>⚠️ 这一列是给运营判断「是不是定价偏高」用的（见类注释），
     * 所以它必须是<b>用户真的会付的那个数</b>。显示 99999、收藏一堆、没人兑，
     * 得出的结论会是「降价」，而那件商品其实只要 1000 分。
     *
     * <p>规则本体在 SQL 里（{@code MallFavoriteMapper.xml}）而不是
     * {@code MallPricing}：这是管理端统计，没有会员上下文，不吃等级折扣，
     * 而且拿 TOP N 行去内存里逐个算价会让「一次查完」变成 N+1。
     */
    private Integer pointsPrice;

    /** 可用库存合计 */
    private Integer availableStock;

    /** 收藏数 */
    private Long favoriteCount;

    /** 累计已兑件数 */
    private Integer soldCount;
}
