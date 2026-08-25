/**
 * 商城-商品 api 封装
 *
 * 写操作只有 save 一个口子（生成器留下的 add / update 已废弃）：
 * 商品是主子表，拆成多个请求的话「主表存进去了、SKU 失败」会留下半截商品，
 * 而前端没有能力回滚已经成功的那几个请求。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:29:59
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const mallCommodityApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/mallCommodity/queryPage', param);
  },

  /**
   * 商品详情：主表 + SKU + 轮播图，一次查完，编辑页回显用  @author  weolwo
   */
  detail: (id) => {
    return getRequest(`/mallCommodity/detail/${id}`);
  },

  /**
   * 「生成」按钮：给运营一个可用的候选编码。运营也可以自己手输，两条路径都由 save 判重  @author  weolwo
   */
  generateCode: () => {
    return getRequest('/mallCommodity/generateCode');
  },

  /**
   * 批量生成 SKU 编码，供「生成/刷新 SKU 表」一次取走。同样只是候选值，判重在保存时做  @author  weolwo
   */
  generateSkuCodes: (count) => {
    return getRequest(`/mallCommodity/generateSkuCodes/${count}`);
  },

  /**
   * 聚合保存：不传 id 即新建。返回商品 id  @author  weolwo
   */
  save: (param) => {
    return postRequest('/mallCommodity/save', param);
  },

  /**
   * 上架 / 下架  @author  weolwo
   */
  updateStatus: (id, status) => {
    return getRequest(`/mallCommodity/updateStatus/${id}/${status}`);
  },

  /**
   * 删除  @author  weolwo
   */
  delete: (id) => {
    return getRequest(`/mallCommodity/delete/${id}`);
  },

  /**
   * 批量删除  @author  weolwo
   */
  batchDelete: (idList) => {
    return postRequest('/mallCommodity/batchDelete', idList);
  },
};
