/**
 * 商城-兑换订单 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:35:46
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const mallOrderApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/mallOrder/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
      return postRequest('/mallOrder/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
      return postRequest('/mallOrder/update', param);
  },



};
