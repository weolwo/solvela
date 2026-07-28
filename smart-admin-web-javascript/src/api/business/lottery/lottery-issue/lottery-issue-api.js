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
   * 删除  @author  weolwo
   */
  delete: (id) => {
      return getRequest(`/lotteryIssue/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
      return postRequest('/lotteryIssue/batchDelete', idList);
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
