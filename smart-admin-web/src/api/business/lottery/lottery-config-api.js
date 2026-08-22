/**
 * 彩票配置 api 封装
 *
 * 🔴 没有 add / update / delete。玩法的写操作统一收口到工作台的 workbench/save
 * （彩票配置 + 奖级规则单事务），扁平表单配不出能上线的玩法；
 * 删除也不给 —— t_lottery_record 存着 lottery_code，删配置会让已发出的号码断链。
 * 停售用 offline，见 LotteryConfigController 的类注释。
 *
 * @Author:    weolwo
 * @Date:      2026-04-19 11:16:39
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const lotteryConfigApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/lotteryConfig/queryPage', param);
  },

  /**
   * 玩法一览：号码空间占用、期号与发号实况、参与人数与体检告警。列表页主视图。
   *
   * queryPage 保留给下拉选项那类只要 lotteryCode/lotteryName 的场景 ——
   * 那些地方不需要为了一个下拉去跑一遍全量体检  @author  alaric
   */
  board: (param) => {
    return postRequest('/lotteryConfig/board', param);
  },

  /**
   * 上线：允许开始发号。服务端会校验必须已配奖级规则  @author  alaric
   */
  online: (lotteryCode) => {
    return getRequest(`/lotteryConfig/online/${lotteryCode}`);
  },

  /**
   * 下线（列表页的「禁用」）：停止发号。已发出的号码不受影响，期号照常可以开奖  @author  alaric
   */
  offline: (lotteryCode) => {
    return getRequest(`/lotteryConfig/offline/${lotteryCode}`);
  },

  /**
   * 批量下线（列表页的「批量禁用」）。入参是 lotteryCode 数组，返回一句人话汇总。
   * 不是全成或全败：已经是下线态的算跳过，不会把其余的一起回滚  @author  alaric
   */
  batchOffline: (lotteryCodeList) => {
    return postRequest('/lotteryConfig/batchOffline', lotteryCodeList);
  },

};
