/**
 * 账务域-会员资金/积分主账表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:17:33
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const memberWalletApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/memberWallet/queryPage', param);
  },

  /**
   * 钱包统计：资产存量 + 本期变动。
   *
   * ⚠️ 余额是**存量、全量**，不随时间范围变化：钱包表一个会员一种资产一行、只有当前余额，
   * 没有历史切片。按创建时间筛出来的是「今天新开的钱包」，那个数什么也回答不了。
   * 跟时间范围走的只有 flowList（本期变动），它取自交易明细表  @author  alaric
   */
  stat: (param) => {
    return postRequest('/memberWallet/stat', param);
  },

  /*
   * 不再封装增删改 —— 后端对应的四个接口已整组移除（v3.69.0）。
   *
   * 尤其是 update：它能直接改余额**且不写任何流水**，改完钱包与交易明细就永久对不上，
   * 而统计面板的体检也发现不了（那里只查得出「余额为负」这种明显异常）。
   * 余额的唯一合法入口是钱包服务里「改余额 + 落流水」绑在同一个事务的那几个方法。
   *
   * 原先的 delete 还是物理删除，删掉一个账户等于把这个人的资产凭空抹掉。
   */
};
