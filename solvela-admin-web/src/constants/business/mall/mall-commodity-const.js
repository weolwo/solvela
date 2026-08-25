/**
 * 商城-商品 枚举与常量
 *
 * 与后端 sa.mall.constant.MallConst 同源，改动时两边必须一起改。
 * 取值口径的唯一真源是 DDL 注释（数据库SQL脚本/mall.sql）。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:29:59
 * @Copyright  weolwo
 */

/**
 * 商品状态。新建默认落草稿 —— 运营配一个商品往往要分几次填完，
 * 中途保存不该让它出现在 C 端。
 */
export const COMMODITY_STATUS_ENUM = {
  OFF: { value: 0, desc: '已下架', color: 'default' },
  ON: { value: 1, desc: '已上架', color: 'green' },
  DRAFT: { value: 2, desc: '草稿', color: 'orange' },
};

export const COMMODITY_STATUS_OPTIONS = Object.values(COMMODITY_STATUS_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/**
 * 商品类型：决定走哪条履约通道，取值对齐后端 PrizeTypeEnum。
 * 不含 SCORE（积分换积分无意义）。
 */
export const COMMODITY_TYPE_ENUM = {
  PHYSICAL: { value: 'PHYSICAL', desc: '实物', tip: '兑换后落发货台，走物流发货' },
  COUPON: { value: 'COUPON', desc: '优惠券', tip: '兑换后直接发券，必须填券模编码' },
  BALANCE: { value: 'BALANCE', desc: '现金/红包', tip: '兑换后入会员钱包' },
};

export const COMMODITY_TYPE_OPTIONS = Object.values(COMMODITY_TYPE_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/**
 * 支付方式。选「纯积分」时现金价由服务端强制归零 ——
 * 前端也要跟着把现金输入框禁掉，否则运营会填一个永远不生效的值然后来问为什么。
 */
export const PAY_TYPE_ENUM = {
  POINTS: { value: 1, desc: '纯积分' },
  POINTS_CASH: { value: 2, desc: '积分 + 现金' },
};

export const PAY_TYPE_OPTIONS = Object.values(PAY_TYPE_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/**
 * 限兑周期，取值对齐 t_promotion_config.limit_period 的既有字典
 */
export const LIMIT_PERIOD_ENUM = {
  LIFETIME: { value: 'LIFETIME', desc: '终身' },
  DAILY: { value: 'DAILY', desc: '每日' },
  WEEKLY: { value: 'WEEKLY', desc: '每周' },
  MONTHLY: { value: 'MONTHLY', desc: '每月' },
};

export const LIMIT_PERIOD_OPTIONS = Object.values(LIMIT_PERIOD_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

export const SKU_STATUS_ENUM = {
  DISABLED: { value: 0, desc: '停用' },
  ENABLED: { value: 1, desc: '启用' },
};

/**
 * 上架有效期的哨兵值：后端的列是 NOT NULL 的，「不限」用这两个值表达
 * （可空的话查询里的 `IS NULL OR <= now()` 走不了索引，DDL 里专门解释过）。
 *
 * 前端的职责只有一条：**看到哨兵值就渲染成空的时间选择器**，
 * 运营留空时传 null，由服务端填回哨兵。不要在前端两边翻译，来回转换必错。
 */
export const SHELF_START_SENTINEL = '1970-01-01 00:00:00';
export const SHELF_END_SENTINEL = '2099-12-31 23:59:59';

export default {
  COMMODITY_STATUS_ENUM,
  COMMODITY_TYPE_ENUM,
  PAY_TYPE_ENUM,
  LIMIT_PERIOD_ENUM,
  SKU_STATUS_ENUM,
};
