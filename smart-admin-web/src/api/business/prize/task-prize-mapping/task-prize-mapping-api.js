/**
 * 任务阶段与奖励映射表 api 封装
 *
 * 🔴 只有查询。这张表由任务配置向导整体托管（保存时按 taskConfigId 全删重插），
 * 单条增删改在这里做了也会被下一次向导保存抹掉；停发奖励的正确做法是把任务下线。
 * 服务端对应的 add/update/delete/batchDelete 接口已一并移除。
 *
 * @Author:    weolwo
 * @Date:      2026-04-18 20:41:02
 * @Copyright  weolwo
 */
import { postRequest } from '/@/lib/axios';

export const taskPrizeMappingApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/taskPrizeMapping/queryPage', param);
  },

};
