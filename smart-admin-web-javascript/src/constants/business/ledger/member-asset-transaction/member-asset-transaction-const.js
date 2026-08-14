/**
 * 账务域-资产变动交易明细表 枚举
 *
 * 取值对齐后端 t_member_asset_transaction 的列注释。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:11:19
 * @Copyright  weolwo
 */

/**
 * 资金流向：对齐 t_member_asset_transaction.transaction_type
 *
 * change_amount 存的是绝对值，方向只看这一列。
 */
export const TRANSACTION_TYPE_ENUM = {
  INCOME: { value: 1, desc: '收入', color: 'green' },
  EXPENSE: { value: 2, desc: '支出', color: 'red' },
};

export const TRANSACTION_TYPE_OPTIONS = Object.values(TRANSACTION_TYPE_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 资产类型：对齐 t_member_asset_transaction.asset_type，与 PrizeTypeEnum、钱包表 asset_type 同一字典
 */
export const ASSET_TYPE_ENUM = {
  SCORE: { value: 'SCORE', desc: '积分', color: 'blue' },
  BALANCE: { value: 'BALANCE', desc: '现金', color: 'green' },
};

export const ASSET_TYPE_OPTIONS = Object.values(ASSET_TYPE_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

export function transactionTypeOf(value) {
  return Object.values(TRANSACTION_TYPE_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function assetTypeOf(value) {
  return Object.values(ASSET_TYPE_ENUM).find((i) => i.value === value) || { desc: value || '-', color: 'default' };
}

export default {
  TRANSACTION_TYPE_ENUM,
  TRANSACTION_TYPE_OPTIONS,
  ASSET_TYPE_ENUM,
  ASSET_TYPE_OPTIONS,
  transactionTypeOf,
  assetTypeOf,
};
