/**
 * 彩票工作台 api 封装
 *
 * @Author:    alaric
 * @Date:      2026-07-27
 */
import { getRequest, postRequest } from '/src/lib/axios';

export const lotteryWorkbenchApi = {
  /**
   * 生成彩票编码（10位大写字母+数字，服务端已判重）  @author  alaric
   */
  generateCode: () => {
    return getRequest('/lotteryConfig/generateCode');
  },

  /**
   * 玩法下拉：按活动过滤。一个活动可以有多个玩法（不同号码长度/发行量就是不同玩法）  @author  alaric
   */
  optionList: (activityCode) => {
    return getRequest('/lotteryConfig/optionList', { activityCode });
  },

  /**
   * 聚合回显。lotteryCode 传空表示「在该活动下新建玩法」，
   * 返回带预生成编码的空壳，前端据此进入「从零配置」态  @author  alaric
   */
  detail: (activityCode, lotteryCode) => {
    return getRequest('/lotteryConfig/workbench/detail', { activityCode, lotteryCode });
  },

  /**
   * 聚合保存（彩票配置 + 奖级规则，单事务）  @author  alaric
   */
  save: (param) => {
    return postRequest('/lotteryConfig/workbench/save', param);
  },

  /**
   * FPE 算号推演。
   * lotteryCode 必传：密钥由 lotteryCode + 期号派生，缺了它算出的号码与线上对不上  @author  alaric
   */
  fpePreview: (param) => {
    return postRequest('/lotteryConfig/fpe/preview', param);
  },
};
