/**
 * 账务域-会员资金/积分主账表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:17:33
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const memberWalletApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/memberWallet/queryPage', param);
  },

  /**
   * 钱包统计：资产存量 + 本期变动。
   *
   * ⚠️ 余额是**存量、全量**，不随时间范围变化：钱包表一个会员一种资产一行、只有当前余额，
   * 没有历史切片。按创建时间筛出来的是「今天新开的钱包」，那个数什么也回答不了。
   * 跟时间范围走的只有 flowList（本期变动），它取自交易明细表  @author  alaric
   */
  stat: (param) => {
    return postRequest('/memberWallet/stat', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
    return postRequest('/memberWallet/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
    return postRequest('/memberWallet/update', param);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/memberWallet/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/memberWallet/batchDelete', idList);
  },
};
