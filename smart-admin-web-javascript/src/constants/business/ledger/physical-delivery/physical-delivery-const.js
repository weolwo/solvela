/**
 * 资产域-实物发货物流表 枚举
 *
 * 取值对齐后端 t_physical_delivery 的列注释与 PhysicalAssetHandler 的常量。
 *
 * @Author:    weolwo
 * @Date:      2026-04-04 16:12:19
 * @Copyright  weolwo
 */

/**
 * 发货状态：对齐 t_physical_delivery.status
 *
 * 中奖时履约单先落 0-待发货，收件信息由用户后续补填（详见 v3.37.0.sql）。
 */
export const DELIVERY_STATUS_ENUM = {
  PENDING: { value: 0, desc: '待发货', color: 'orange' },
  DELIVERED: { value: 1, desc: '已发货', color: 'blue' },
  SIGNED: { value: 2, desc: '已签收', color: 'green' },
  RETURNED: { value: 3, desc: '异常退回', color: 'red' },
};

export const DELIVERY_STATUS_OPTIONS = Object.values(DELIVERY_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

export function deliveryStatusOf(value) {
  return Object.values(DELIVERY_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export default {
  DELIVERY_STATUS_ENUM,
  DELIVERY_STATUS_OPTIONS,
  deliveryStatusOf,
};
