/**
 * 商城-SKU与库存 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:37:50
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const mallSkuApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/mallSku/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
      return postRequest('/mallSku/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
      return postRequest('/mallSku/update', param);
  },



};
