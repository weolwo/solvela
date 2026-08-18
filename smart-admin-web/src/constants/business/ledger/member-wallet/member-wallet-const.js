/**
 * 账务域-会员资金/积分主账表 枚举
 *
 * 取值对齐后端 t_member_wallet 的列注释。
 *
 * @Author:    weolwo
 * @Date:      2026-04-03 17:17:33
 * @Copyright  weolwo
 */

/**
 * 钱包状态：对齐 t_member_wallet.status
 *
 * 冻结的钱包不参与扣减/入账（见 MemberWallet#status 的校验）。
 */
export const WALLET_STATUS_ENUM = {
  FROZEN: { value: 0, desc: '冻结', color: 'red' },
  NORMAL: { value: 1, desc: '正常', color: 'green' },
};

export const WALLET_STATUS_OPTIONS = Object.values(WALLET_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 资产类型：对齐 t_member_wallet.asset_type，与 PrizeTypeEnum、流水表 asset_type 同一字典
 */
export const ASSET_TYPE_ENUM = {
  SCORE: { value: 'SCORE', desc: '积分', color: 'blue' },
  BALANCE: { value: 'BALANCE', desc: '现金', color: 'green' },
};

export const ASSET_TYPE_OPTIONS = Object.values(ASSET_TYPE_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

/**
 * 资产计量单位：余额只在同一 assetType 内可加，展示时必须带上单位，
 * 否则「5830」既可能是积分也可能是元，看的人只能猜。
 *
 * 直接复用提案域那一份，不在这里再抄一遍 —— t_member_wallet.asset_type 与
 * t_proposal_record.asset_type 本来就是同一个字典（后端同一个 PrizeTypeEnum）。
 */
export { assetUnitOf } from '/@/constants/business/risk/proposal-record/proposal-record-const';

export function walletStatusOf(value) {
  return Object.values(WALLET_STATUS_ENUM).find((i) => i.value === value) || { desc: '-', color: 'default' };
}

export function assetTypeOf(value) {
  return Object.values(ASSET_TYPE_ENUM).find((i) => i.value === value) || { desc: value || '-', color: 'default' };
}

export default {
  WALLET_STATUS_ENUM,
  WALLET_STATUS_OPTIONS,
  ASSET_TYPE_ENUM,
  ASSET_TYPE_OPTIONS,
  walletStatusOf,
  assetTypeOf,
};
