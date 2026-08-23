/**
 * 商城-商品分类 api 封装
 *
 * 写操作只有 save 一个口子（生成器留下的 add / update 已废弃）：
 * 那两个表单一个强制要求传 id（新建时根本没有），一个只有 id 一个字段（什么都改不了）。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:28:16
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const mallCategoryApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/mallCategory/queryPage', param);
  },

  /**
   * 全部分类（不分页）。列表页用它自己拼两级树 ——
   * 分页会把「父在第 2 页、子在第 1 页」这种拼不出树的情况带进来  @author  weolwo
   */
  queryAll: () => {
    return getRequest('/mallCategory/queryAll');
  },

  /**
   * 启用中的分类列表（不分页）：商品编辑页的分类下拉用。
   *
   * 不复用 queryPage —— 下拉要的是「全部启用分类」，用分页接口就得传一个猜出来的 pageSize，
   * 分类超过那个数以后新建的商品选不到它，而这种漏项是静默的。
   * 服务端还会把「父级已停用的子分类」一并排除  @author  weolwo
   */
  enabledList: () => {
    return getRequest('/mallCategory/enabledList');
  },

  /**
   * 保存：不传 id 即新建。返回分类 id  @author  weolwo
   */
  save: (param) => {
    return postRequest('/mallCategory/save', param);
  },

  /**
   * 批量新建：一次建多个，可带一层子分类。
   *
   * 子分类嵌在父节点的 children 里而不是平铺 + 填 parentId —— 父分类此刻还没入库，
   * 前端填不出那个自增 id，只能由服务端在一个事务里先建父再建子  @author  weolwo
   */
  batchSave: (param) => {
    return postRequest('/mallCategory/batchSave', param);
  },

  /**
   * 启用 / 停用  @author  weolwo
   */
  updateStatus: (id, status) => {
    return getRequest(`/mallCategory/updateStatus/${id}/${status}`);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/mallCategory/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/mallCategory/batchDelete', idList);
  },
};
