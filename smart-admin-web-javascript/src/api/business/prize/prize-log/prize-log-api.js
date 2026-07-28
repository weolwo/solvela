/**
 * 资产域-奖励发放执行明细与快照表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 18:42:42
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const prizeLogApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/prizeLog/queryPage', param);
  },

  /**
   * 审批通过：approve_mode=1 奖品的唯一出口，通过后立即走完整派发链路。
   * 服务端用条件更新（WHERE approve_status=1）做并发闸门，两人同时点第二次会拿到「已被处理」  @author  alaric
   */
  approve: (id) => {
    return getRequest(`/prizeLog/approve/${id}`);
  },

  /**
   * 审批驳回：不再派发，记录留痕  @author  alaric
   */
  reject: (id, reason) => {
    return getRequest(`/prizeLog/reject/${id}`, { reason });
  },

  // ⚠️ 刻意不封装 add / update / delete：
  // t_prize_log 是「实际发了什么奖」的审计流水，由派发链路写入。
  // 手工增改会让流水与真实发放脱节 —— 而运营看的、对账依据的正是这张表。
  // 需要人工干预的场景只有审批（上面两个），以及必要时的人工订正（走 DBA 流程并留痕）。
};
