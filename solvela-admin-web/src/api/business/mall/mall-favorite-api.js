/**
 * 商城-商品收藏 api 封装
 *
 * 只有一个统计接口。收藏是 C 端用户的行为，后台既不该增删，也不需要看逐条明细 ——
 * 「张三收藏了 T 恤」这条记录本身回答不了任何运营问题。
 * 生成器留的分页查询与增删改已全部废弃。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:34:44
 * @Copyright  weolwo
 */
import { getRequest } from '/@/lib/axios';

export const mallFavoriteApi = {
  /**
   * 收藏统计与排行  @author  weolwo
   */
  queryStat: (rankTopN) => {
    return getRequest(rankTopN ? `/mallFavorite/queryStat?rankTopN=${rankTopN}` : '/mallFavorite/queryStat');
  },
};
