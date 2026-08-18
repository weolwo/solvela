/**
 * 账务域-资产变动交易明细表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:11:19
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const memberAssetTransactionApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/memberAssetTransaction/queryPage', param);
  },

  /**
   * 交易统计：按资产类型的收支与净额、业务类型分布、账务体检。
   *
   * ⚠️ 入参只有时间范围（statDateBegin / statDateEnd，含结束日），不吃列表的筛选项 ——
   * 面板回答的是「这段时间整体发生了什么」，跟着某个会员名筛就不再是概览了。
   * 不传日期时服务端落到**数据库当天**。
   *
   * ⚠️ 金额按资产类型分开返回，页面上也不要相加：积分和现金不是同一个量纲  @author  alaric
   */
  stat: (param) => {
    return postRequest('/memberAssetTransaction/stat', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
    return postRequest('/memberAssetTransaction/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
    return postRequest('/memberAssetTransaction/update', param);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/memberAssetTransaction/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/memberAssetTransaction/batchDelete', idList);
  },
};
