/*
 * 营销统计 api
 *
 * 口径见 docs/首页图表-口径确认清单.md
 */
import { getRequest } from '/@/lib/axios';

export const marketingStatApi = {
  /**
   * 首页参与统计：趋势 + 各玩法类型参与人数
   * @param days 统计天数，默认 7，上限 90
   */
  participation: (days) => {
    return getRequest('/marketingStat/participation', { days });
  },
};
