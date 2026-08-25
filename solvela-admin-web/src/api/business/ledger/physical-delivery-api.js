/**
 * 资产域-实物发货物流表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-04 16:12:19
 * @Copyright  weolwo
 */
import { postRequest, getRequest, getDownload } from '/src/lib/axios';

export const physicalDeliveryApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/physicalDelivery/queryPage', param);
  },

  /**
   * 发货统计：本期新增 + 待发货积压与收件信息体检。
   *
   * ⚠️ 只有「本期新增」跟时间范围走，**积压那一组是全量**：
   * 把积压限制在今天，压了三天的单子会正好从页面上消失，
   * 而那恰恰是这个页面唯一需要有人动手的东西。
   *
   * ⚠️ 待发货分成两类：收件信息没补全的（想发也发不了，要催用户）
   * 和地址齐了等发货的（运营今天真正能干的活）  @author  alaric
   */
  stat: (param) => {
    return postRequest('/physicalDelivery/stat', param);
  },

  /**
   * 增加  @author  weolwo
   */
  add: (param) => {
    return postRequest('/physicalDelivery/add', param);
  },

  /**
   * 修改  @author  weolwo
   */
  update: (param) => {
    return postRequest('/physicalDelivery/update', param);
  },

  /**
   * 删除  @author  weolwo
   */
  discard: (id) => {
    return getRequest(`/physicalDelivery/discard/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDiscard: (idList) => {
    return postRequest('/physicalDelivery/batchDiscard', idList);
  },

  /**
   * 导入模板下载：新增履约单
   */
  downloadAddTemplate: () => {
    return getDownload('/physicalDelivery/importAddTemplate');
  },

  /**
   * 导入模板下载：回填物流
   */
  downloadShipTemplate: () => {
    return getDownload('/physicalDelivery/importShipTemplate');
  },

  /**
   * 导入：新增履约单
   */
  importAdd: (formData) => {
    return postRequest('/physicalDelivery/importAdd', formData);
  },

  /**
   * 导入：回填物流
   */
  importShip: (formData) => {
    return postRequest('/physicalDelivery/importShip', formData);
  },

  /**
   * 导出：履约单
   */
  exportOrder: (formData) => {
    return postRequest('/physicalDelivery/exportOrder', formData);
  },
};
