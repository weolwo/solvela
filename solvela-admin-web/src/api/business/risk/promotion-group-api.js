/**
 * 风控域-优惠配置分组 api 封装
 *
 * @Author:    alaric
 * @Date:      2026-08-30
 */
import { getRequest, postRequest } from '/src/lib/axios';

export const promotionGroupApi = {
  /** 分页查询 */
  queryPage: (param) => {
    return postRequest('/promotionGroup/queryPage', param);
  },

  /**
   * 工作台聚合回显。不传 id 即「新建」，服务端返回一个带默认值的空壳，
   * 前端不必为新建与编辑写两套初始化逻辑。
   */
  workbenchDetail: (id) => {
    return getRequest(id ? `/promotionGroup/workbenchDetail?id=${id}` : '/promotionGroup/workbenchDetail');
  },

  /** 工作台聚合保存，返回分组 ID（新建后要用它把页面切到编辑态） */
  workbenchSave: (param) => {
    return postRequest('/promotionGroup/workbenchSave', param);
  },

  /**
   * 分组主开关。
   * 停用：组内配置全部一起停用。
   * 启用：必须带 enablePrizeTypes 指定要开哪几种类型（组内有配置时不能为空）。
   */
  updateStatus: (param) => {
    return postRequest('/promotionGroup/updateStatus', param);
  },

  /**
   * 复制分组：连同组内每种类型的配置一起复制成新的一份。
   * 服务端会重发编码、清空配置ID、并把 used_quota / used_amount 归零。
   */
  copy: (param) => {
    return postRequest('/promotionGroup/copy', param);
  },

  /**
   * 组内单条配置的发放开关。
   * 传的是【优惠配置ID】不是分组ID。组停用时不允许单独启用某个类型，服务端会拦。
   */
  updateItemStatus: (param) => {
    return postRequest('/promotionGroup/updateItemStatus', param);
  },

  /** 解散分组：组删掉，组内配置保留并变回未分组 */
  delete: (id) => {
    return getRequest(`/promotionGroup/delete/${id}`);
  },
};
