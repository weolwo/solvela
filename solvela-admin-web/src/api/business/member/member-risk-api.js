/**
 * 登录风控 api 封装。
 *
 * 三个页面：规则（能改档位）、决策流水（只读）、看板。都在「安全中心」菜单下。
 *
 * 🔴 **规则没有新增、没有删除**：一条规则对应一个 Java 类，代码里有而表里没有会让服务
 * 启动失败。所以后台能做的只有调「松紧」—— 档位、分值、参数。
 *
 * @Date 2026-10-16
 */
import { postRequest } from '/@/lib/axios';

export const memberRiskApi = {
  /**
   * 规则列表。前置规则在前、后置在后（按表里的 id 顺序）。
   */
  listRules: () => {
    return postRequest('/memberRiskRule/list');
  },

  /**
   * 改档位 / 分值 / 参数。
   *
   * ⚠️ params 不传表示不改；传空串表示清空、回到代码里的默认值。
   * 保存时服务端会用**这条规则自己的**校验过一遍 —— 形状不对会直接报错，
   * 而不是存进去之后让这条规则悄悄失效。
   */
  updateRule: (param) => {
    return postRequest('/memberRiskRule/update', param);
  },

  /**
   * 决策流水分页。
   *
   * ⚠️ 不传时间范围时服务端兜最近 7 天 —— 这张表按月分区，不带时间会扫所有分区。
   */
  queryEventPage: (param) => {
    return postRequest('/memberRiskEvent/queryPage', param);
  },

  /**
   * 流水详情：结论 + 每条命中的规则。
   *
   * 🔴 **createTime 必须跟着传**（列表每一行都带着它，原样回传即可）：
   * 表按月分区，只按 id 查不裁剪分区。
   */
  eventDetail: (param) => {
    return postRequest('/memberRiskEvent/detail', param);
  },

  // 名单的接口 2026-10-17 搬到了 security/security-api.js（名单归安全中心）

  /**
   * 看板：结论分布 / 矩阵各格 / 核身漏斗 / 规则命中，**四组数一次取回**。
   *
   * 🔴 它们共用同一个时间窗 —— 拆成四个请求就有机会用四个不同的窗去问，
   * 然后把对不上的数摆在同一个屏幕上。
   *
   * ⚠️ 不传时间兜最近 30 天；一次最多 90 天（更长服务端直接报错，不会悄悄截断）。
   */
  stat: (param) => {
    return postRequest('/memberRiskStat/stat', param);
  },
};
