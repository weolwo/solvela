/**
 * 商城-库存总览 api 封装
 *
 * 只读。改库存在商品编辑页（那里有批量设置，且与价格、状态同一个聚合保存事务）——
 * 两个入口写同一批数据迟早对不上，所以生成器留的 add / update 已废弃。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:37:50
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const mallSkuApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/mallSku/queryPage', param);
  },





};
