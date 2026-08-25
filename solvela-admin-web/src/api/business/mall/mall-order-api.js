/**
 * 商城-兑换订单 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:35:46
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const mallOrderApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/mallOrder/queryPage', param);
  },

  /**
   * 统计 + 兑换商品排行。收的是<b>和列表同一个查询表单</b> ——
   * 顶部筛选改了统计跟着变，两套条件的话运营会看到「统计说 100 单、列表只有 3 条」  @author  weolwo
   */
  queryStat: (param) => {
    return postRequest('/mallOrder/queryStat', param);
  },





};
