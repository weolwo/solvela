/**
 * 风控域-优惠配置分组 枚举
 *
 * @Author:    alaric
 * @Date:      2026-08-30
 */
// 注意导入的是【奖品配置】那份字典而不是同目录的 promotion-config-const：
// dispatchable（这个类型发不发得出去）只长在前者身上，后者只有展示用的 color
import { PRIZE_TYPE_ENUM } from '/src/constants/business/prize/prize-config-const';

/**
 * 分组状态：对齐 t_promotion_group.status
 *
 * ⚠️ 它是组内所有配置的**主开关**，不是一个独立的标记位：
 * 关掉分组会连带把组内每一条 t_promotion_config 一起停用。
 * 反过来不对称 —— 开启时必须显式选要开哪几种类型，因为「关之前哪些是开的」
 * 在关的那一刻就被覆盖掉了，猜一个默认（比如全开）会把本来该停发的类型重新放出去。
 */
export const GROUP_STATUS_ENUM = {
  DISABLED: { value: 0, desc: '停用', color: 'default' },
  ENABLED: { value: 1, desc: '启用', color: 'green' },
};

export const GROUP_STATUS_OPTIONS = Object.values(GROUP_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

export function groupStatusOf(value) {
  return Object.values(GROUP_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

/**
 * 能进分组的资产类型。
 *
 * 判据直接复用奖品配置那份字典的 dispatchable —— 没有派发策略的类型配了也发不出去。
 * MARKER 额外排除：它不动账、不进提案，预算/库存/风控/审批四样一个都用不上，
 * t_prize_config.promotion_config_id 对它已经是可空的。服务端
 * PromotionGroupService.GROUPABLE_PRIZE_TYPES 是同一份名单，两边必须一致。
 */
export const GROUPABLE_PRIZE_TYPES = Object.values(PRIZE_TYPE_ENUM)
  .filter((item) => item.dispatchable && item.value !== 'MARKER')
  .map((item) => ({ value: item.value, desc: item.desc }));

export default {
  GROUP_STATUS_ENUM,
  GROUP_STATUS_OPTIONS,
  GROUPABLE_PRIZE_TYPES,
  groupStatusOf,
};
