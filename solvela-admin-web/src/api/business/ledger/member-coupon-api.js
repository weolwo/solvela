/**
 * 资产域-会员优惠券实例表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:15:39
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/src/lib/axios';

export const memberCouponApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/memberCoupon/queryPage', param);
  },

  /**
   * 优惠券统计：本期发放、本期核销、券库存与过期体检。
   *
   * ⚠️ 发放和核销是**两个口径**：发放按 create_time，核销按 used_time。
   * 两个数没有包含关系，页面上不要相减 —— 今天核销的券可能是上个月发的。
   *
   * ⚠️ stock 开头的那几个字段是**全量**，不随时间范围变化：
   * 压着多少张没用的券是存量问题，限制在今天只会把它藏起来  @author  alaric
   */
  stat: (param) => {
    return postRequest('/memberCoupon/stat', param);
  },

  /*
   * 不再封装增删改 —— 后端对应的四个接口已整组移除（v3.69.0）。
   * 这是账务台账：券由发奖链路发放、由核销链路回写，改一行就让台账和真实发生过的事脱节；
   * 原先的 delete 还是物理删除，删完连「少了什么」都查不出来。
   * 真需要人工订正，走提案链路或 DBA 流程并留痕。
   */
};
