package solvela.mall.favorite.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.mall.favorite.domain.entity.MallFavorite;
import solvela.mall.favorite.domain.vo.MallFavoriteRankVO;
import solvela.mall.favorite.domain.vo.MallFavoriteStatVO;

import java.util.List;

/**
 * 商城-商品收藏 Dao
 *
 * <p><b>没有分页明细查询</b>：一条收藏记录是「某人收藏了某商品」，逐条看没有运营价值 ——
 * 运营要回答的是「哪些商品最多人想要」。所以这里只有三段聚合。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:34:44
 * @Copyright weolwo
 */
@Mapper
public interface MallFavoriteDao extends BaseMapper<MallFavorite> {

    /**
     * 总量统计：收藏总数 / 被收藏商品数 / 有收藏行为的会员数
     */
    MallFavoriteStatVO queryStat();

    /**
     * 收藏排行（按收藏数）
     */
    List<MallFavoriteRankVO> queryRank(@Param("topN") int topN);

    /**
     * 「想要但买不到」：有人收藏，而商品已下架或可用库存为 0
     */
    List<MallFavoriteRankVO> queryUnavailableRank(@Param("topN") int topN);
}
