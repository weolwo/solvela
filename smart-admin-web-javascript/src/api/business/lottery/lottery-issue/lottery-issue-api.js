/**
 * 期号配置 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-19 11:23:43
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const lotteryIssueApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/lotteryIssue/queryPage', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
      return postRequest('/lotteryIssue/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
      return postRequest('/lotteryIssue/update', param);
  },


  /**
   * 删除：只能删「零发号 + 待开奖」的空期，服务端有守卫。
   * 用在工作台 Tab2 清理误建的期号；列表页不给这个口子  @author  weolwo
   */
  delete: (id) => {
      return getRequest(`/lotteryIssue/delete/${id}`);
  },

  /**
   * 停售（列表页的「禁用」）：把售卖结束时间提前到此刻，立刻停止发号。
   *
   * 期号没有「启用/禁用」这一维 —— status 是生命周期（待开奖/核销中/已开奖），不是开关。
   * 「现在还能不能领号」的判据在售卖窗口上，所以停售就改窗口，不另加标志位  @author  alaric
   */
  stopSale: (id) => {
    return getRequest(`/lotteryIssue/stopSale/${id}`);
  },

  /**
   * 批量停售。已停售/已开奖的计入跳过，不是全成或全败，返回一句人话汇总  @author  alaric
   */
  batchStopSale: (idList) => {
    return postRequest('/lotteryIssue/batchStopSale', idList);
  },

  /**
   * 服务端摇号：SecureRandom 生成。
   * 开奖号码是资损敏感数据，前端 Math.random 生成不可信  @author  alaric
   */
  randomNumber: (issueId) => {
    return getRequest('/lotteryIssue/randomNumber', { issueId });
  },

  /**
   * 执行开奖核销。核销中重复调用会接着跑，不会重复认领  @author  alaric
   */
  settle: (issueId, winningNumber) => {
    return postRequest(`/lotteryIssue/settle?issueId=${issueId}&winningNumber=${winningNumber}`);
  },

  /**
   * 核销进度：中奖/未中奖/待派奖各多少  @author  alaric
   */
  settleSummary: (issueId) => {
    return getRequest('/lotteryIssue/settleSummary', { issueId });
  },

  /**
   * 触发派奖：把中奖记录分批投递进公共派发链路  @author  alaric
   */
  dispatch: (issueId) => {
    return postRequest(`/lotteryIssue/dispatch?issueId=${issueId}`);
  },

};
