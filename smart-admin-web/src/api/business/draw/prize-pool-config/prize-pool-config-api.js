/**
 * 奖池配置 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-19 09:42:12
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const prizePoolConfigApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/prizePoolConfig/queryPage', param);
  },

  /**
   * 奖池一览：可编辑字段 + 坑位/概率/限领搭配的体检结论。列表页主视图。
   *
   * queryPage 保留给下拉选项那类只要 poolCode/poolName 的场景 —— 那些地方不需要
   * 为了一个下拉去跑一遍全量体检  @author  alaric
   */
  board: (param) => {
    return postRequest('/prizePoolConfig/board', param);
  },

  /**
   * 修改：只改奖池名称与限领重置周期  @author  weolwo
   */
  update: (param) => {
      return postRequest('/prizePoolConfig/update', param);
  },

  /*
   * ⚠️ add / delete / batchDelete 已移除，服务端也一并下掉：
   *
   *  - 新建奖池 → 抽奖工作台。只有奖池行、没有坑位映射的池，抽奖时快照构造
   *    直接抛「奖池快照不能为空」，是个建了就用不了的空壳。
   *  - 删除 → 禁用。t_draw_prize_log 里存着 pool_code，那是发奖凭证；
   *    池删了，用户说「我明明在这个池抽中过」就再也自证不了。
   *    禁用不动任何历史数据，运行态立即拒绝新请求，而且可逆。
   */

  /**
   * 禁用奖池：关闭开关，运行态立即拒绝新的抽奖请求。历史流水与坑位不受影响  @author  alaric
   */
  offline: (id) => {
    return getRequest(`/prizePoolConfig/offline/${id}`);
  },

  /**
   * 启用奖池：把开关拨回开启。禁用是可逆的，这是那条回头路  @author  alaric
   */
  online: (id) => {
    return getRequest(`/prizePoolConfig/online/${id}`);
  },

  /**
   * 批量禁用。本就已关闭的计入跳过，返回一句人话汇总，不是全成或全败  @author  alaric
   */
  batchOffline: (idList) => {
    return postRequest('/prizePoolConfig/batchOffline', idList);
  },

};
