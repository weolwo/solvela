/**
 * 商城-兑换订单 枚举与常量
 *
 * 与后端 sa.mall.constant.MallConst 同源，改动时两边必须一起改。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:35:46
 * @Copyright  weolwo
 */

/**
 * 订单状态。取值是**跳跃的**（0/10/20/...），不是连续序号 —— DDL 就是这么定的，
 * 留出空档是为了将来插入中间态（比如「待发货」）时不用重排既有值。
 * 所以别写 `status < 30` 这种范围判断，只能逐个比。
 */
export const ORDER_STATUS_ENUM = {
  UNPAID: { value: 0, desc: '待支付', color: 'orange' },
  PENDING: { value: 10, desc: '待履约', color: 'blue' },
  FULFILLING: { value: 20, desc: '履约中', color: 'processing' },
  FINISHED: { value: 30, desc: '已完成', color: 'green' },
  CANCELLED: { value: 40, desc: '已取消', color: 'default' },
  REFUNDED: { value: 50, desc: '已退款', color: 'purple' },
  FAILED: { value: 60, desc: '履约失败', color: 'red' },
};

export const ORDER_STATUS_OPTIONS = Object.values(ORDER_STATUS_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/**
 * 订单来源。FLASH_SALE 对应的秒杀模块还没做，但 DDL 已经把这个值写进注释了 ——
 * 留着它，将来上线秒杀时后台不用改。
 */
export const ORDER_SOURCE_ENUM = {
  NORMAL: { value: 'NORMAL', desc: '日常兑换' },
  FLASH_SALE: { value: 'FLASH_SALE', desc: '限时抢购' },
};

export const ORDER_SOURCE_OPTIONS = Object.values(ORDER_SOURCE_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

export default {
  ORDER_STATUS_ENUM,
  ORDER_SOURCE_ENUM,
};
