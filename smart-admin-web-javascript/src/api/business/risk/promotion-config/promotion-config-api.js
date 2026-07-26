/**
 * 资产域-优惠配置(预算与风控兜底)表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 18:44:52
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const promotionConfigApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/promotionConfig/queryPage', param);
  },

  /**
   * 优惠配置下拉列表（全量启用中，前端按 prizeType 分组做级联）  @author  alaric
   */
  optionList: () => {
    return getRequest('/promotionConfig/optionList');
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
    return postRequest('/promotionConfig/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
    return postRequest('/promotionConfig/update', param);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/promotionConfig/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/promotionConfig/batchDelete', idList);
  },
};
