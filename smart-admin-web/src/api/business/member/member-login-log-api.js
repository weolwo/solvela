/**
 * 会员登录日志 api 封装
 *
 * 登录日志是 append-only 的（DDL 注释，且按月分区），所以没有增删改 ——
 * 生成器留下的 add / update 对着一张只进不出的表，调了只会写脏数据。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 20:58:39
 * @Copyright  weolwo
 */
import { postRequest } from '/@/lib/axios';

export const memberLoginLogApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/memberLoginLog/queryPage', param);
  },

  /**
   * 登录统计。收的是<b>和列表同一个查询表单</b> —— 顶部筛选改了统计跟着变，
   * 两套条件的话运营会看到「统计说 100 次、列表只有 3 条」然后不知道信哪个  @author  weolwo
   */
  queryStat: (param) => {
    return postRequest('/memberLoginLog/queryStat', param);
  },
};
