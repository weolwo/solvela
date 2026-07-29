/**
 * 活动配置 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-18 19:31:49
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const activityConfigApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/activityConfig/queryPage', param);
  },

  /**
   * 生成活动编码（10位大写字母+数字，服务端已判重）  @author  alaric
   */
  generateCode: () => {
    return getRequest('/activityConfig/generateCode');
  },

  /**
   * 活动下拉列表：activityType 可传 BASIC/DRAW/TASK/LOTTERY 过滤，不传则全部  @author  alaric
   *
   * includeInactive 默认 false，服务端会过滤掉已下线与已过期的活动。
   * 注意它<b>不会</b>过滤 status=0 未开始 —— 向导刚创建的活动就是这个状态，
   * 过滤掉它，新建活动将永远进不了工作台。
   */
  optionList: (activityType, includeInactive = false) => {
    return getRequest('/activityConfig/optionList', { activityType, includeInactive });
  },

  /**
   * 批量查询「玩法是否已配置完备」，供列表页一次算完  @author  alaric
   * 入参 activityCode 数组，返回 { [activityCode]: boolean }
   */
  configuredStatus: (activityCodeList) => {
    return postRequest('/activityConfig/configuredStatus', activityCodeList);
  },

  /**
   * 删除前检查：返回 { deletable, reason, refs }  @author  alaric
   */
  checkDeletable: (id) => {
    return getRequest(`/activityConfig/checkDeletable/${id}`);
  },

  /**
   * 升级活动类型：仅 BASIC → DRAW/TASK/LOTTERY  @author  alaric
   */
  upgradeType: (param) => {
    return postRequest('/activityConfig/upgradeType', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
    return postRequest('/activityConfig/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
    return postRequest('/activityConfig/update', param);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/activityConfig/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/activityConfig/batchDelete', idList);
  },
};
