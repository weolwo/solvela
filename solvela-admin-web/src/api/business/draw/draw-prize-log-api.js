/**
 * 抽奖记录 api 封装 —— 流水只读。
 *
 * ⚠️ add / update / delete / batchDelete 已刻意移除，服务端也一并下掉了这四个接口。
 *
 * 抽奖流水是发奖凭证与对账依据：用户说「我明明抽中了」、财务对「这个月发了多少奖」，
 * 依据都是这张表。后台能改能删，等于这套审计不存在。
 * 而且这四个接口本来就没有正当用途 —— 流水由抽奖链路自己写入，
 * 没有任何场景需要人工补录或修改一条抽奖记录。
 *
 * 清理数据走 DBA 脚本（见「抽奖模块-联调造数.sql」清场章节），
 * 那里要求同时清 Redis 的库存与限领计数，只删流水表会留下更不一致的状态。
 *
 * @Author:    weolwo
 * @Date:      2026-04-19 09:21:26
 * @Copyright  weolwo
 */
import { postRequest } from '/src/lib/axios';

export const drawPrizeLogApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/drawPrizeLog/queryPage', param);
  },

  /**
   * 抽奖转化漏斗：中奖率、库存不足率、参与人数、奖品发放分布。
   *
   * 刻意不吃 status 筛选 —— 它是漏斗要拆解的维度本身，
   * 跟着筛的话点「只看库存不足」会让漏斗变成「库存不足 100%」  @author  alaric
   */
  funnel: (param) => {
    return postRequest('/drawPrizeLog/funnel', param);
  },
};
