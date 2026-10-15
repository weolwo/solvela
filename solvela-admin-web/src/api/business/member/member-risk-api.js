/**
 * 登录风控 api 封装。
 *
 * 两个页面：规则（能改档位）、决策流水（只读）。
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
};
