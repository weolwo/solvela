/**
 * 彩票奖励配置 api 封装 —— 只读。
 *
 * ⚠️ add / update / delete / batchDelete 已刻意移除，服务端也一并下掉了这四个接口。
 *
 * 奖级规则的唯一写入口是「彩票配置工作台」。原先这里那套写接口有两个问题：
 *   ① 绕过全部校验 —— 工作台会拦下奖级99、匹配长度超号码长度、奖品不在本活动资产库等六类问题，
 *      而这条路径一条都不查。写进去的脏规则后果是实打实的：
 *      规则值非法 → 开奖时该奖级被整条跳过；奖品编码不存在 → 用户中了奖拿不到东西。
 *   ② 写进去也活不长 —— 工作台按玩法整表重建奖级（先删后插），从这里加的规则会被静默删掉。
 *
 * @Author:    weolwo
 * @Date:      2026-04-19 11:50:34
 * @Copyright  weolwo
 */
import { postRequest } from '/@/lib/axios';

export const lotteryPrizeRuleApi = {
  /**
   * 分页查询：奖级规则原始行，留给排查用  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/lotteryPrizeRule/queryPage', param);
  },

  /**
   * 奖励结构分析：按玩法给出净中奖率、预计中奖注数、预计赔付成本与配置体检告警。
   *
   * 概览与明细在同一次请求里算出来，两者不可能对不上  @author  alaric
   */
  analysis: (param) => {
    return postRequest('/lotteryPrizeRule/analysis', param);
  },
};
