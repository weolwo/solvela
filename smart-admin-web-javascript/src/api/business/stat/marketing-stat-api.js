/*
 * 营销统计 api
 *
 * 口径见 docs/首页图表-口径确认清单.md 与 docs/营销中台-数据统计方案.md。
 *
 * ⚠️ 七个接口共用一个权限串 marketingStat:query，功能点见 v3.52.0.sql。
 *    库里没有功能点的权限串 = 除超管外谁也用不了，且界面上看不出线索。
 *
 * ⚠️ 冷热分开（方案 §4）：overview / prizeHealth / gameplay 是热接口（30s），
 *    taskFunnel / eventHealth / topMembers 是冷接口（5min）。轮询节流在大屏组件里做。
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

  /** 大屏总览：活动状态分布（启用/禁用两态）+ 活动卡片 + 逾期未开奖告警 */
  overview: () => {
    return getRequest('/marketingStat/overview');
  },

  /**
   * 发奖健康度
   * @param activityCode 不传即全局
   * @param days 趋势窗口；条数与价值是全量，不受它影响
   */
  prizeHealth: (activityCode, days) => {
    return getRequest('/marketingStat/prizeHealth', { activityCode, days });
  },

  /** 玩法运行态：按活动类型返回抽奖状态分布 / 彩票期号 / 任务列表 */
  gameplay: (activityCode) => {
    return getRequest(`/marketingStat/gameplay/${activityCode}`);
  },

  /** 任务阶梯流失漏斗 */
  taskFunnel: (taskConfigId) => {
    return getRequest(`/marketingStat/taskFunnel/${taskConfigId}`);
  },

  /** 事件健康度：推进/丢弃，丢弃按 discard_code 聚类 */
  eventHealth: (days) => {
    return getRequest('/marketingStat/eventHealth', { days });
  },

  /** Top 获奖用户 */
  topMembers: (days) => {
    return getRequest('/marketingStat/topMembers', { days });
  },
};
