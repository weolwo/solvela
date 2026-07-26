/**
 * 业务级-发奖规则与奖品明细表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 18:39:36
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const prizeConfigApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/prizeConfig/queryPage', param);
  },

  /**
   * 生成奖品编码（10位大写字母+数字，服务端已判重）  @author  alaric
   */
  generateCode: () => {
    return getRequest('/prizeConfig/generateCode');
  },

  /**
   * 活动下启用中的奖品列表（抽奖工作台「从资产大库引入奖项」用）  @author  alaric
   */
  optionList: (activityCode) => {
    return getRequest('/prizeConfig/optionList', { activityCode });
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
    return postRequest('/prizeConfig/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
    return postRequest('/prizeConfig/update', param);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/prizeConfig/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/prizeConfig/batchDelete', idList);
  },
};
