/**
 * 会员等级 枚举与常量
 *
 * 与后端 solvela.member.level.LevelChangeType 同源，改动时两边必须一起改。
 *
 * 🔴 等级本身【不在这里】：它是 t_member_grade 里的配置，运营随时能加一档。
 *    在前端写死一份等级字典，加档那天页面上就会出现「等级 5」而没有名字。
 *    所有需要等级下拉的地方都走 memberGradeApi.listConfig()。
 *
 * @Author:    alaric
 * @Date:      2026-09-18
 */

export const GRADE_CHANGE_TYPE_ENUM = {
  UPGRADE: { value: 'UPGRADE', desc: '升级', color: 'green' },
  DOWNGRADE: { value: 'DOWNGRADE', desc: '降级', color: 'red' },
  KEEP: { value: 'KEEP', desc: '保级', color: 'blue' },
  /** 人工调整用醒目色：审计时第一眼要能从一屏系统变更里挑出人改过的那几条 */
  MANUAL: { value: 'MANUAL', desc: '人工调整', color: 'orange' },
  RISK_REVOKE: { value: 'RISK_REVOKE', desc: '风控扣回', color: 'volcano' },
};

export const GRADE_CHANGE_TYPE_OPTIONS = Object.values(GRADE_CHANGE_TYPE_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/**
 * 成长值来源。DDL 注释写着「SCORE_EARNED / 将来的 BIND_PHONE 等」——
 * 省略号表示字典是开放的，所以这里只给查询下拉用，不要拿它校验写入。
 */
export const GROWTH_SOURCE_OPTIONS = [{ value: 'SCORE_EARNED', label: '积分入账' }];

/** 取枚举项；取不到时回退成「原样显示 + 默认色」，不要显示成空白 */
export function gradeChangeTypeMeta(value) {
  return GRADE_CHANGE_TYPE_ENUM[value] || { value, desc: value || '—', color: 'default' };
}

export const ENABLE_STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];
