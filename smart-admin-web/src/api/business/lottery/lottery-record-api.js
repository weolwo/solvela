/**
 * 用户号码记录 api 封装 —— 只读。
 *
 * ⚠️ add / update 已刻意移除，服务端也一并下掉。
 *
 * 这张表存的是用户手里的号码本身，比抽奖流水更不能碰：
 *   - security_sign 是防篡改签名，用户凭它自证「这号码确实是系统发给我的」，
 *     后台能改签名，整套自证机制就是摆设；
 *   - win_status / prize_level / prize_code 是派奖依据，改一行等于凭空造一个中奖者；
 *   - ticket_number 与 sequence_no 是 FPE 双射的两端，改任一个都会让号码反解验真失败。
 *
 * 而且这两个接口本来就没有正当用途 —— 记录由领号链路写入、由开奖核销 SQL 批量更新。
 *
 * @Author:    weolwo
 * @Date:      2026-04-19 11:57:08
 * @Copyright  weolwo
 */
import { postRequest } from '/src/lib/axios';

export const lotteryRecordApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/lotteryRecord/queryPage', param);
  },

  /**
   * 购彩漏斗：中奖率、奖级分布、派发状态与数据一致性体检。
   *
   * 刻意不吃 winStatus / prizeLevel 筛选 —— 那两个正是漏斗要拆解的维度，
   * 跟着筛的话点「只看已中奖」会让中奖率变成 100%  @author  alaric
   */
  funnel: (param) => {
    return postRequest('/lotteryRecord/funnel', param);
  },
};
