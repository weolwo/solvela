/**
 * 会员登录日志（append-only，按月分区） api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 20:58:39
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const memberLoginLogApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/memberLoginLog/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
      return postRequest('/memberLoginLog/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
      return postRequest('/memberLoginLog/update', param);
  },



};
