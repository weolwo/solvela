package solvela.mall.favorite.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品收藏统计。
 *
 * <p><b>这个页面只做统计，没有明细列表</b>：一条收藏记录是「某人收藏了某商品」，
 * 逐条看它没有任何运营价值 —— 运营要回答的是「哪些商品最多人想要」。
 *
 * @Date 2026-08-23
 */
@Data
public class MallFavoriteStatVO {

    @Schema(description = "收藏总数")
    private Long totalCount;

    @Schema(description = "被收藏的商品数")
    private Long commodityCount;

    @Schema(description = "有收藏行为的会员数")
    private Long memberCount;

    @Schema(description = "收藏排行")
    private List<MallFavoriteRankVO> rank = new ArrayList<>();

    /**
     * 「想要但买不到」：收藏数不为 0，而商品已下架或可用库存为 0。
     *
     * <p>这是这个页面最有价值的一块 —— 它直接对应一个可执行的动作：补货或重新上架。
     * 单纯的收藏排行看不出这一层，因为榜首很可能正是一个早就卖空的爆款。
     */
    @Schema(description = "有人想要但当前买不到的商品（已下架或零库存）")
    private List<MallFavoriteRankVO> unavailableRank = new ArrayList<>();
}
