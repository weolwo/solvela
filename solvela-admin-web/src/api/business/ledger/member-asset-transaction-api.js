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

  /*
   * 不再封装增删改 —— 后端对应的四个接口已整组移除（v3.69.0）。
   * 这是账务域唯一的资金流水，钱包余额的每一次变动都在这里留了一行：
   * 改一行就让账面和真实发生过的事脱节，而对账正是以它为准；
   * 原先的 delete 还是物理删除，删完连「少了什么」都查不出来。
   */
};
