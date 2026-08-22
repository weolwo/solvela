/**
 * 商城-会员限兑计数 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:33:25
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const mallExchangeLimitApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/mallExchangeLimit/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
      return postRequest('/mallExchangeLimit/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
      return postRequest('/mallExchangeLimit/update', param);
  },


  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
      return getRequest(`/mallExchangeLimit/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
      return postRequest('/mallExchangeLimit/batchDelete', idList);
  },

};
