/**
 * 抽奖工作台 api 封装
 *
 * @Author:    alaric
 * @Date:      2026-07-26
 */
import { postRequest } from '/src/lib/axios';

export const drawWorkbenchApi = {
  /**
   * 聚合保存（物资 + 多奖池 + 坑位映射，归属后端 poolconfig 模块）  @author  alaric
   */
  save: (param) => {
    return postRequest('/prizePoolConfig/workbench/save', param);
  },
};
