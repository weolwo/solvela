/**
 * 提案表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-18 23:13:50
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const proposalRecordApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/proposalRecord/queryPage', param);
  },

  /**
   * 提案漏斗：到账率、审批积压、下发卡单、资产/来源分布与流程体检。
   * ⚠️ 服务端刻意忽略 status 筛选项 —— 它是漏斗要拆解的维度，跟着筛会让到账率恒为 100%；
   * assetType / sourceType / 时间范围这些切片维度照常生效  @author  alaric
   */
  funnel: (param) => {
    return postRequest('/proposalRecord/funnel', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
      return postRequest('/proposalRecord/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
      return postRequest('/proposalRecord/update', param);
  },


  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
      return getRequest(`/proposalRecord/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
      return postRequest('/proposalRecord/batchDelete', idList);
  },

};
