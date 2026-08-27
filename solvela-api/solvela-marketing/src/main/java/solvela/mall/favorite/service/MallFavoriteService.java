package solvela.mall.favorite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.base.domain.ResponseDTO;
import solvela.mall.constant.MallConst;
import solvela.mall.favorite.dao.MallFavoriteDao;
import solvela.mall.favorite.domain.dto.MallFavoriteStatDTO;

import java.util.List;

/**
 * 商城-商品收藏 Service
 *
 * <p><b>只读、只出统计</b>。收藏是 C 端用户的行为，后台既不该增删，也不需要看逐条明细 ——
 * 「张三收藏了 T 恤」这条记录本身回答不了任何运营问题。
 *
 * <p>它真正的价值是<b>需求信号</b>：想要但还没兑。所以统计里除了排行，
 * 还单独给出「有人想要但当前买不到」的那一批 —— 那是唯一直接对应动作（补货 / 重新上架）的部分。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:34:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallFavoriteService {

    private final MallFavoriteDao mallFavoriteDao;

    public ResponseDTO<MallFavoriteStatDTO> queryStat(Integer rankTopN) {
        int topN = rankTopN == null ? MallConst.RANK_TOP_N : rankTopN;
        topN = Math.min(Math.max(topN, 1), MallConst.MAX_RANK_TOP_N);

        MallFavoriteStatDTO stat = mallFavoriteDao.queryStat();
        if (stat == null) {
            // 一条收藏都没有时聚合返回 null，直接下发会让前端把「0」渲染成空白
            stat = new MallFavoriteStatDTO();
            stat.setTotalCount(0L);
            stat.setCommodityCount(0L);
            stat.setMemberCount(0L);
        }
        stat.setRank(nullToEmpty(mallFavoriteDao.queryRank(topN)));
        stat.setUnavailableRank(nullToEmpty(mallFavoriteDao.queryUnavailableRank(topN)));
        return ResponseDTO.ok(stat);
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
