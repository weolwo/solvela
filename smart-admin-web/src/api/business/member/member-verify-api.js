/**
 * 会员实名信息（敏感，与主表分离） api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 21:00:09
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const memberVerifyApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/memberVerify/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
      return postRequest('/memberVerify/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
      return postRequest('/memberVerify/update', param);
  },



};
