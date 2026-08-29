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

    /** 基准兑换积分 */
    private Integer pointsPrice;

    /** 可用库存合计 */
    private Integer availableStock;

    /** 收藏数 */
    private Long favoriteCount;

    /** 累计已兑件数 */
    private Integer soldCount;
}
