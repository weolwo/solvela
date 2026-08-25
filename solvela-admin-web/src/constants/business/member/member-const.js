/**
 * 会员模块 枚举与常量
 *
 * 与后端 sa.member.constant.MemberConst 同源，改动时两边必须一起改。
 * 取值口径的唯一真源是 DDL 注释（数据库SQL脚本/member.sql）。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:39:08
 * @Copyright  weolwo
 */

export const MEMBER_STATUS_ENUM = {
  NORMAL: { value: 1, desc: '正常', color: 'green' },
  FROZEN: { value: 2, desc: '冻结', color: 'red' },
  /**
   * 已注销是**终态**：注销会把 phone_hash 置 NULL 以释放号码，那个动作不可逆。
   * 列表里这一行的开关要禁掉 —— 改回「正常」只会得到一个登录不了、也找不回的账号。
   */
  CANCELLED: { value: 3, desc: '已注销', color: 'default' },
};

export const MEMBER_STATUS_OPTIONS = Object.values(MEMBER_STATUS_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

export const GENDER_ENUM = {
  UNKNOWN: { value: 0, desc: '未知' },
  MALE: { value: 1, desc: '男' },
  FEMALE: { value: 2, desc: '女' },
};

export const GENDER_OPTIONS = Object.values(GENDER_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/**
 * 注册来源渠道。DDL 里是 varchar 且注释写着「H5/APP/WECHAT/INVITE/IMPORT...」——
 * 省略号表示这个字典是开放的，所以查询下拉给常见值即可，不要拿它去校验写入。
 */
export const REGISTER_SOURCE_OPTIONS = [
  { value: 'H5', label: 'H5' },
  { value: 'APP', label: 'APP' },
  { value: 'WECHAT', label: '微信' },
  { value: 'INVITE', label: '邀请' },
  { value: 'IMPORT', label: '后台导入' },
  { value: 'UNKNOWN', label: '未知' },
];

/**
 * 登录日志状态。
 *
 * ⚠️ 与 t_login_log.login_result **取值相反**（DDL 注释里专门标了这一条）：
 * 那张是员工登录日志，这张是会员登录日志，别照着另一张的语义读这一列。
 */
export const LOGIN_STATUS_ENUM = {
  FAIL: { value: 0, desc: '失败', color: 'red' },
  SUCCESS: { value: 1, desc: '成功', color: 'green' },
  LOGOUT: { value: 2, desc: '登出', color: 'default' },
};

export const LOGIN_STATUS_OPTIONS = Object.values(LOGIN_STATUS_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

export const DEVICE_TYPE_OPTIONS = [
  { value: 'APP', label: 'APP' },
  { value: 'H5', label: 'H5' },
  { value: 'WECHAT', label: '微信' },
  { value: 'PC', label: 'PC' },
];

/**
 * 实名认证状态。**只有「认证中」能被审核** —— 已认证的再点一次会把 verify_time
 * 刷成今天，而那个时间是合规审计要回答「什么时候通过的」的依据。
 */
export const VERIFY_STATUS_ENUM = {
  NONE: { value: 0, desc: '未认证', color: 'default' },
  PENDING: { value: 1, desc: '认证中', color: 'processing' },
  VERIFIED: { value: 2, desc: '已认证', color: 'green' },
  FAILED: { value: 3, desc: '认证失败', color: 'red' },
};

export const VERIFY_STATUS_OPTIONS = Object.values(VERIFY_STATUS_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/** 按枚举取展示文案，取不到就把原值显示出来 —— 不要显示成空白，那会让人以为数据丢了 */
export function descOf(enumObj, value) {
  const meta = Object.values(enumObj).find((item) => item.value === value);
  return meta ? meta.desc : value;
}

export function metaOf(enumObj, value, fallback) {
  return Object.values(enumObj).find((item) => item.value === value) || fallback;
}

export default {
  MEMBER_STATUS_ENUM,
  GENDER_ENUM,
  LOGIN_STATUS_ENUM,
  VERIFY_STATUS_ENUM,
};
