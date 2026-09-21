/**
 * 设备模块 枚举与常量。
 *
 * 取值口径的唯一真源是 DDL 注释（数据库SQL脚本/mysql/schema-baseline.sql 里的 t_device）
 * 与后端 `DeviceStatusEnum`，改动时三边一起改。
 *
 * @Date  2026-09-10
 */

/**
 * 处置档。**三档是「降级优先」的表达，不是三种严厉程度**。
 *
 * 命中限流规则的第一反应是推到「观察」（登录多验一道验证码），而不是「封禁」——
 * 误伤的代价不对称：拦错一个正常用户，他不会来报障，只会不再打开。
 */
export const DEVICE_STATUS_ENUM = {
  NORMAL: { value: 0, desc: '正常', color: 'green' },
  OBSERVE: { value: 1, desc: '观察', color: 'orange' },
  BANNED: { value: 2, desc: '封禁', color: 'red' },
};

export const DEVICE_STATUS_OPTIONS = Object.values(DEVICE_STATUS_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));

/**
 * 可信度。**目前只有 0 一档是真实存在的** —— 全仓没有接任何厂商证明，
 * 1 和 2 是给将来留的档位。列表里如实显示，不要让它看起来像已经在用。
 */
export const ATTEST_LEVEL_ENUM = {
  SELF_REPORT: { value: 0, desc: '仅自报', color: 'default' },
  CODE_VERIFIED: { value: 1, desc: '验证码通过', color: 'blue' },
  VENDOR_ATTESTED: { value: 2, desc: '厂商证明', color: 'green' },
};
