/**
 * 抽奖工作台 api 封装
 *
 * @Author:    alaric
 * @Date:      2026-07-26
 */
import { getRequest, postRequest } from '/src/lib/axios';

export const drawWorkbenchApi = {
  /**
   * 生成奖池编码（10位大写字母+数字，服务端已判重）  @author  alaric
   */
  generatePoolCode: () => {
    return getRequest('/prizePoolConfig/generateCode');
  },

  /**
   * 聚合回显（返回结构与 save 入参同构，可直接填回两个 Tab）  @author  alaric
   */
  detail: (activityCode) => {
    return getRequest('/prizePoolConfig/workbench/detail', { activityCode });
  },

  /**
   * 聚合保存（物资 + 多奖池 + 坑位映射，归属后端 poolconfig 模块）  @author  alaric
   */
  save: (param) => {
    return postRequest('/prizePoolConfig/workbench/save', param);
  },
};
