/**
 * 安全中心的枚举：名单、安全事件、限流规则、处罚。**与后端一一对应，取值不能自己改**。
 *
 * 后端：SecurityListTypeEnum / SecurityListDimensionEnum / SecurityListStatusEnum、
 *      SecurityIncidentEvent（事件的类型、处置、压下原因、命中位置）、t_rate_limit_rule（维度、档位）
 *
 * 风控规则、决策流水、看板的枚举仍在 member/risk-const.js（那是会员域的登录风控）。
 *
 * @Date 2026-10-17
 */

function optionsOf(enumObj) {
  return Object.values(enumObj).map((item) => ({ label: item.desc, value: item.value }));
}

// ---------------------------------------------------------------- 名单

/**
 * 名单类型。
 *
 * 🔴 **豁免面刻意很窄**，页面上要写出来，否则客服会以为「加了白名单就万事大吉」：
 * IP 白名单只跳过 IP 维度的计数与限流；会员白名单只跳过矩阵与打分 ——
 * 全局滑块、密码冷却、设备封禁一个都不豁免。
 */
export const SECURITY_LIST_TYPE_ENUM = {
  BLACK: { value: 1, desc: '黑名单', color: 'red' },
  GREY: { value: 2, desc: '灰名单', color: 'orange' },
  WHITE: { value: 3, desc: '白名单', color: 'green' },
};

export const SECURITY_LIST_TYPE_OPTIONS = optionsOf(SECURITY_LIST_TYPE_ENUM);

/** 名单维度。会员维度**没有黑名单**（封号走会员状态，不在这里），只有白、灰 */
export const SECURITY_LIST_DIMENSION_ENUM = {
  IP: { value: 1, desc: 'IP', color: 'blue' },
  IP_RANGE: { value: 2, desc: 'IP段', color: 'cyan' },
  IDENTITY: { value: 3, desc: '身份摘要', color: 'purple' },
  MEMBER: { value: 4, desc: '会员', color: 'gold' },
};

export const SECURITY_LIST_DIMENSION_OPTIONS = optionsOf(SECURITY_LIST_DIMENSION_ENUM);

/** 名单状态。撤销不删行 —— 「谁加的、谁解的、为什么」都要查得到 */
export const SECURITY_LIST_STATUS_ENUM = {
  REVOKED: { value: 0, desc: '已撤销', color: 'default' },
  VALID: { value: 1, desc: '有效', color: 'green' },
};

/** 自动条目的来源（t_security_list.source_rule）。人工条目为空 */
export const SECURITY_LIST_SOURCE_RULE_ENUM = {
  HONEYPOT: { value: 'HONEYPOT', desc: '蜜罐接口', color: 'volcano' },
  CANARY: { value: 'CANARY', desc: '蜜罐账号', color: 'red' },
  KEYWORD: { value: 'KEYWORD', desc: '关键字', color: 'magenta' },
  RATE_LIMIT: { value: 'RATE_LIMIT', desc: '限流', color: 'orange' },
  ESCALATE: { value: 'ESCALATE', desc: '黑到期转灰', color: 'gold' },
  SEGMENT: { value: 'SEGMENT', desc: '同段连坐', color: 'purple' },
};

// ---------------------------------------------------------------- 安全事件

export const SECURITY_INCIDENT_TYPE_ENUM = {
  HONEYPOT: { value: 1, desc: '蜜罐接口', color: 'volcano' },
  CANARY: { value: 2, desc: '蜜罐账号', color: 'red' },
  KEYWORD: { value: 3, desc: '关键字', color: 'magenta' },
  RATE_LIMIT: { value: 4, desc: '限流判罚 / 升档', color: 'orange' },
  // 低置信、只记录：请求体里有请求对象没有的字段。前后端版本错开时真人也会撞到
  UNKNOWN_FIELD: { value: 5, desc: '未知字段', color: 'default' },
};

export const SECURITY_INCIDENT_TYPE_OPTIONS = optionsOf(SECURITY_INCIDENT_TYPE_ENUM);

/** 实际对 IP 做了什么 */
export const SECURITY_INCIDENT_ACTION_ENUM = {
  RECORD: { value: 0, desc: '仅记录', color: 'default' },
  GREY: { value: 1, desc: '拉灰', color: 'orange' },
  BLACK: { value: 2, desc: '拉黑', color: 'red' },
};

/** 处罚被哪道保险压下了 */
export const SECURITY_INCIDENT_SUPPRESS_ENUM = {
  NONE: { value: 0, desc: '—', color: 'default' },
  CROSS_SITE: { value: 1, desc: '跨站发起', color: 'blue' },
  BREAKER: { value: 2, desc: '熔断中', color: 'purple' },
  OLD_DEVICE: { value: 3, desc: '老设备豁免（仅 IP）', color: 'cyan' },
};

/** 命中位置 */
export const SECURITY_INCIDENT_FIELD_ENUM = {
  UA: { value: 1, desc: 'UA' },
  PATH: { value: 2, desc: '路径' },
  QUERY: { value: 3, desc: '查询参数' },
  BODY: { value: 4, desc: '请求体' },
};

// ---------------------------------------------------------------- 限流规则

/** t_rate_limit_rule.dimension */
export const RATE_LIMIT_DIMENSION_ENUM = {
  GLOBAL: { value: 0, desc: '全局', color: 'default' },
  IP: { value: 1, desc: 'IP', color: 'blue' },
  ACCOUNT: { value: 2, desc: '账号', color: 'gold' },
  DEVICE: { value: 3, desc: '设备', color: 'green' },
  IP_SEGMENT: { value: 4, desc: 'IP段', color: 'cyan' },
};

export const RATE_LIMIT_DIMENSION_OPTIONS = optionsOf(RATE_LIMIT_DIMENSION_ENUM);

/** 档位。陪跑 = 照常计数、只记「本来会怎样」，不挡人 */
export const RATE_LIMIT_MODE_ENUM = {
  OFF: { value: 0, desc: '关闭', color: 'default' },
  SHADOW: { value: 1, desc: '陪跑', color: 'blue' },
  ENFORCE: { value: 2, desc: '生效', color: 'red' },
};

export const RATE_LIMIT_MODE_OPTIONS = optionsOf(RATE_LIMIT_MODE_ENUM);

/** t_rate_limit_rule.target_type */
export const RATE_LIMIT_TARGET_TYPE_ENUM = {
  SCENE: { value: 1, desc: '场景' },
  ENDPOINT: { value: 2, desc: '接口' },
};

/** 网关的场景（RateLimitScene）。匿名场景的账号是请求体里的目标 —— 那里的账号维度永远不拉灰会员 */
export const RATE_LIMIT_SCENE_ENUM = {
  LOGIN: { value: 'LOGIN', desc: '登录' },
  REGISTER: { value: 'REGISTER', desc: '注册' },
  CODE_SEND: { value: 'CODE_SEND', desc: '发验证码' },
  PASSWORD_RESET: { value: 'PASSWORD_RESET', desc: '重置密码' },
  REDEEM: { value: 'REDEEM', desc: '兑换 / 领取' },
  SIGN: { value: 'SIGN', desc: '签到' },
  CAPTCHA: { value: 'CAPTCHA', desc: '滑块' },
  DEFAULT_WRITE: { value: 'DEFAULT_WRITE', desc: '默认写' },
  DEFAULT_READ: { value: 'DEFAULT_READ', desc: '默认读' },
  EXEMPT: { value: 'EXEMPT', desc: '不限流' },
};

// ---------------------------------------------------------------- 处罚

/** 处罚的种类 */
export const SECURITY_PENALTY_KIND_ENUM = {
  GLOBAL: { value: 'GLOBAL', desc: '全局处罚（所有接口）', color: 'red' },
  RATE_LIMIT: { value: 'RATE_LIMIT', desc: '限流处罚（单个场景）', color: 'orange' },
  BANNED: { value: 'BANNED', desc: '设备封禁', color: 'magenta' },
};

/** 蜜罐 / 关键字的种类（后端 SecurityGateAdminService.Kind） */
export const SECURITY_GATE_KIND = {
  HONEYPOT: 'HONEYPOT',
  KEYWORD: 'KEYWORD',
};

/** 关键字规则作用在哪 */
export const SECURITY_KEYWORD_SCOPE_OPTIONS = [
  { label: '请求参数（路径、查询串、JSON 请求体）', value: 'PARAM' },
  { label: 'User-Agent', value: 'UA' },
];

/**
 * 关键字规则的反例集，照抄后端 KeywordRules.NORMAL_INPUTS / NORMAL_USER_AGENTS。
 *
 * 🔴 **这份只用来在弹窗里做本地预检**：规则保存前服务端会用它自己那份（同一套，连超时判定一起）再跑一遍，
 * 那份才是说了算的。放一份到前端，是因为「写宽了的正则会把真人封 24 小时」这件事
 * 值得在人还在改正则的时候就说出来，而不是等点了保存才弹一条红字。
 *
 * 两边不同步的代价是单向的：前端漏了一条 → 预检放过、服务端照样拒绝（只是晚一步）；
 * 所以这里宁可少、不可多，也不要在这里加后端没有的反例。
 */
export const KEYWORD_NORMAL_INPUTS = [
  'Floor(落地灯)',
  'Doctor(医生)',
  'Color(2024)!',
  'XX路(东门)',
  '解放路100号(东门)3楼',
  'iPhone 15 Pro (256GB)',
  'Sleep(睡眠)眼罩',
  'sleep (8h) 好梦套装',
  'Tea or (coffee)',
  'Rock and Roll (Live)',
  '喜欢(猫)和(狗)',
  'error(404) 页面',
  'for(int i = 0; i < n; i++)',
  "O'Neil",
  "I'm here or there",
  '1+1=2',
  'a=b',
  'x = 1 or 2',
  'price<100',
  '<b>加粗</b>',
  '../readme.md',
  'C:\\Users\\me',
  'JavaScript: The Good Parts',
  'Union Square 联合广场',
  'select 精选好物',
  'Benchmark (2020)',
  'Wait for delay 等待',
  'P@ssw0rd!',
  'a\'b"c',
  '#hashtag',
  '50% off!',
  'user@example.com',
  'https://example.com/path?q=1&b=2',
  '「双十一」限时 5 折',
  'Mr. Information',
  'or',
  'and',
  'union',
  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!',
  '1111111111111111111111111111111111111111x',
];

/** 正常的 User-Agent。🔴 curl / Postman 不是扫描器，不该被封 */
export const KEYWORD_NORMAL_USER_AGENTS = [
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36',
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0',
  'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36',
  'okhttp/4.12.0',
  'curl/8.5.0',
  'PostmanRuntime/7.42.0',
  'Dart/3.5 (dart:io)',
  'Java-http-client/21',
];
