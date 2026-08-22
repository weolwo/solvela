/**
 * 奖池奖项映射 api 封装 —— 只读。
 *
 * ⚠️ add / update / delete / batchDelete 已刻意移除，服务端也一并下掉了这四个接口。
 *
 * 坑位映射的唯一写入口是「抽奖工作台」。原先这里那套写接口零校验，后果是全模块最严重的：
 *   ① 把任意一条概率改得让整池总和 ≠ 100%，DrawPoolSnapshot 构造就抛异常，
 *      而抽奖执行链路没有捕获它 —— 该奖池的每一次抽奖请求都直接报错；
 *   ② 绕过工作台的上线结构锁（已上线活动禁止增删坑位）；
 *   ③ 配出多个兜底，引擎只认第一个，其余静默失效；
 *   ④ 工作台按池整表重建坑位，从这里写进去的也活不过下次保存。
 *
 * @Author:    weolwo
 * @Date:      2026-04-19 10:07:03
 * @Copyright  weolwo
 */
import { postRequest } from '/src/lib/axios';

export const poolPrizeMappingApi = {
  /**
   * 分页查询：坑位映射原始行，留给排查用  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/poolPrizeMapping/queryPage', param);
  },

  /**
   * 奖池概率分析：命中区间、期望赔付、库存双口径对比与体检告警。
   * 概览与明细同一次请求返回，两者不可能对不上  @author  alaric
   */
  analysis: (param) => {
    return postRequest('/poolPrizeMapping/analysis', param);
  },
};
