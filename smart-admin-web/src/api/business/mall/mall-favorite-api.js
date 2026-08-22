/**
 * 商城-商品收藏 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:34:44
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const mallFavoriteApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/mallFavorite/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
      return postRequest('/mallFavorite/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
      return postRequest('/mallFavorite/update', param);
  },



};
