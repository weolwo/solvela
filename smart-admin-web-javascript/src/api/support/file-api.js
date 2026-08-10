/*
 * 文件上传
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-09-03 21:55:25
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */
import { postRequest, getRequest, getDownload } from '/@/lib/axios';

export const fileApi = {
  // 文件上传 @author 卓大
  uploadUrl: '/support/file/upload',
  uploadFile: (param, folder) => {
    return postRequest(`/support/file/upload?folder=${folder}`, param);
  },

  /**
   * 分页查询  @author 卓大
   */
  queryPage: (param) => {
    return postRequest('/support/file/queryPage', param);
  },
  /**
   * 文件分类列表（按后台配置的展示顺序） @author 1024
   */
  getCategoryList: () => {
    return getRequest('/support/file/category/list');
  },

  /**
   * 获取文件URL：根据storageKey @author 胡克
   */
  getUrl: (storageKey) => {
    return getRequest(`/support/file/getFileUrl?storageKey=${storageKey}`);
  },

  /**
   * 下载文件流（根据storageKey） @author 胡克
   */
  downLoadFile: (storageKey) => {
    return getDownload('/support/file/downLoad', { storageKey });
  },

  /**
   * 文件详情。返回里带「被哪些业务引用着」——删图前要看得见 @author 1024
   */
  detail: (fileId) => {
    return getRequest(`/support/file/detail/${fileId}`);
  },

  /**
   * 改名 / 打标签。只动展示层信息，storageKey 不受影响 @author 1024
   */
  updateMeta: (param) => {
    return postRequest('/support/file/updateMeta', param);
  },

  /**
   * 删除文件。有业务引用时后端会拒绝 @author 1024
   */
  delete: (fileId) => {
    return getRequest(`/support/file/delete/${fileId}`);
  },

  /**
   * 按 fileId 下载。inline=true 时浏览器内联展示（图片/PDF 预览） @author 1024
   */
  downloadById: (fileId) => {
    return getDownload(`/support/file/download/${fileId}`, {});
  },

  // ---------------------------- 分类管理 ----------------------------

  addCategory: (param) => {
    return postRequest('/support/file/category/add', param);
  },

  updateCategory: (param) => {
    return postRequest('/support/file/category/update', param);
  },

  deleteCategory: (categoryId) => {
    return getRequest(`/support/file/category/delete/${categoryId}`);
  },

  /**
   * 拖拽排序：传排好序的分类 ID 列表 @author 1024
   */
  reorderCategory: (orderedIds) => {
    return postRequest('/support/file/category/reorder', orderedIds);
  },
};
