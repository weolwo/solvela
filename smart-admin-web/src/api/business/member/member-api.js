/**
 * 会员主表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:39:08
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const memberApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/member/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
      return postRequest('/member/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
      return postRequest('/member/update', param);
  },



};
