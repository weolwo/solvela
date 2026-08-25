/**
 * 奖池奖项库 api 封装 —— 只读。
 *
 * ⚠️ add / update / delete / batchDelete 已刻意移除，服务端也一并下掉了这四个接口。
 *
 * 奖项与库存的唯一写入口是「抽奖工作台」。原先这里那套写接口零校验，其中最危险的一条：
 * 表单直接接受 usedStock —— 跨奖池累计已出数量、库存对账的基准。
 * 工作台落库处明写「used_stock/version 永不接受前端值」，这条路径却照单全收。
 * 手改一个数，DB 账目当场错乱，而运行态真正用来预扣的 Redis 剩余量根本不会跟着变。
 *
 * @Author:    weolwo
 * @Date:      2026-04-19 09:52:45
 * @Copyright  weolwo
 */
import { postRequest } from '/src/lib/axios';

export const prizePoolItemApi = {
  /**
   * 分页查询：奖项原始行，留给排查用  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/prizePoolItem/queryPage', param);
  },

  /**
   * 库存看板：Redis/DB 双口径剩余、消耗率、售罄预警、跨奖池引用与体检告警。
   *
   * 之所以要两个口径：运行态的扣减是 Redis Lua 预扣 + DB 兜底，
   * 决定「还能不能抽到」的是 Redis 那个数，used_stock 只是事后对账用的  @author  alaric
   */
  stockBoard: (param) => {
    return postRequest('/prizePoolItem/stockBoard', param);
  },
};
