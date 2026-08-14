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
  delete: (id) => {
    return getRequest(`/physicalDelivery/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/physicalDelivery/batchDelete', idList);
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
};
