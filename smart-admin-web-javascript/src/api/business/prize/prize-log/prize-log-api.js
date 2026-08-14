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

  /**
   * 人工补发：新建一条待审批的发奖记录。
   *
   * ⚠️ 这里刻意只开放 add，仍然不封装 update / delete —— 原则没变：
   * t_prize_log 是「实际发了什么奖」的审计流水，改历史会让流水与真实发放脱节。
   * 而「补发」是往前追加一条新流水，不是改旧账，它本身就是一次真实发放的起点。
   *
   * 🔴 必须落 approveStatus=1（待审批）：/prizeLog/add 是纯 insert，不触发任何派发。
   * 全工程唯一能把奖真正发出去的入口是 /prizeLog/approve/{id}（PrizeDispatchHandler.approveDispatch），
   * 而它的前置条件正是 approve_status=1。落 0 只会造出一条永远不会到账的死记录。
   * 所以补发是「新建 → 审批通过」两步，第二步才真正走提案与账务。
   */
  add: (param) => {
    return postRequest('/prizeLog/add', param);
  },

  // ⚠️ 依旧不封装 update / delete：改/删已发生的发奖流水会让对账失去依据，
  // 真需要订正走 DBA 流程并留痕。
};
