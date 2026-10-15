/**
 * 登录风控的枚举。**与后端的枚举一一对应，取值不能自己改**。
 *
 * 后端：MemberRiskDecisionEnum / MemberRiskModeEnum / MemberRiskOutcomeEnum /
 *      MemberRiskPhaseEnum / MemberRiskRuleTypeEnum
 *
 * @Date 2026-10-16
 */

/**
 * 处置等级。
 *
 * 🔴 **取值本身就是严厉程度**（后端靠取大小来「取最严的那一档」），
 * 所以这里的颜色也按同一个梯度走 —— 看列表的人应该一眼看出哪几行更严重。
 */
export const RISK_DECISION_ENUM = {
  PASS: { value: 1, desc: '放行', color: 'green' },
  CAPTCHA: { value: 2, desc: '滑块', color: 'blue' },
  OTP: { value: 3, desc: 'OTP', color: 'gold' },
  OTP_STRONG: { value: 4, desc: '换通道OTP', color: 'orange' },
  COOLDOWN: { value: 5, desc: '密码冷却', color: 'volcano' },
  LIMIT: { value: 6, desc: '限流', color: 'red' },
  DENY: { value: 7, desc: '拒绝', color: 'red' },
};

export const RISK_DECISION_OPTIONS = Object.values(RISK_DECISION_ENUM).map((item) => ({
  label: item.desc,
  value: item.value,
}));

/** 规则档位。陪跑 = 照常执行、照常记命中，但不影响结论 */
export const RISK_MODE_ENUM = {
  OFF: { value: 0, desc: '关闭', color: 'default' },
  SHADOW: { value: 1, desc: '陪跑', color: 'blue' },
  ENFORCE: { value: 2, desc: '生效', color: 'green' },
};

export const RISK_MODE_OPTIONS = Object.values(RISK_MODE_ENUM).map((item) => ({
  label: item.desc,
  value: item.value,
}));

/** 这次尝试最后怎么样了。与「风控判了什么」是两件事，所以分开记、分开看 */
export const RISK_OUTCOME_ENUM = {
  SUCCESS: { value: 1, desc: '登录成功', color: 'green' },
  BAD_CREDENTIALS: { value: 2, desc: '凭据错误', color: 'orange' },
  UNKNOWN_ACCOUNT: { value: 3, desc: '查无此人', color: 'volcano' },
  FROZEN: { value: 4, desc: '账号冻结', color: 'red' },
  COOLDOWN: { value: 5, desc: '冷却中', color: 'volcano' },
  BAD_FORMAT: { value: 6, desc: '格式非法', color: 'default' },
  CAPTCHA_REQUIRED: { value: 7, desc: '需要滑块', color: 'blue' },
  PRE_BLOCKED: { value: 8, desc: '前置拦下', color: 'red' },
  POST_BLOCKED: { value: 9, desc: '后置拦下', color: 'red' },
  CHALLENGED: { value: 10, desc: '已签凭票', color: 'gold' },
  VERIFY_PASSED: { value: 11, desc: '验证通过', color: 'green' },
  VERIFY_FAILED: { value: 12, desc: '验证失败', color: 'orange' },
  INTERNAL_ERROR: { value: 13, desc: '内部异常', color: 'red' },
};

export const RISK_OUTCOME_OPTIONS = Object.values(RISK_OUTCOME_ENUM).map((item) => ({
  label: item.desc,
  value: item.value,
}));

export const RISK_PHASE_ENUM = {
  PRE: { value: 1, desc: '前置', color: 'cyan' },
  POST: { value: 2, desc: '后置', color: 'purple' },
};

export const RISK_RULE_TYPE_ENUM = {
  DIRECT: { value: 1, desc: '强规则', color: 'red' },
  TIERED: { value: 2, desc: '分级/矩阵', color: 'orange' },
  SCORE: { value: 3, desc: '打分', color: 'blue' },
};

/** 设备信任。取值见后端 RiskDeviceTrust */
export const RISK_DEVICE_TRUST_ENUM = {
  0: { desc: '无设备', color: 'default' },
  1: { desc: '新设备', color: 'orange' },
  2: { desc: '老交情', color: 'green' },
  3: { desc: '验证过', color: 'green' },
  4: { desc: '注册设备', color: 'green' },
};

/** IP 熟悉度。见后端 RiskIpFamiliarity */
export const RISK_IP_FAMILIAR_ENUM = {
  0: { desc: '陌生', color: 'orange' },
  1: { desc: '同城同运营商', color: 'blue' },
  2: { desc: '常用网段', color: 'green' },
};

/** 核身通道 */
export const RISK_VERIFY_CHANNEL_ENUM = {
  1: { desc: '邮箱' },
  2: { desc: '短信' },
};

/** 登录方式。与后端 MemberLoginType 的序号对应（1 开始，不是 ordinal） */
export const RISK_LOGIN_TYPE_ENUM = {
  1: { desc: '手机号+密码' },
  2: { desc: '邮箱+密码' },
  3: { desc: '邮箱验证码' },
};

/** 按 value 找一项；找不到就用 fallback —— 后端加了新取值而前端还没跟上时，别渲染成空白 */
export function riskMetaOf(enumObj, value, fallback) {
  if (value === null || value === undefined) {
    return fallback;
  }
  const direct = enumObj[value];
  if (direct) {
    return direct;
  }
  const found = Object.values(enumObj).find((item) => item.value === value);
  return found || fallback;
}
