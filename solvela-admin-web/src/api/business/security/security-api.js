/**
 * 安全中心 api 封装：名单、限流规则、处罚、蜜罐与关键字、安全事件、蜜罐账号。
 *
 * 风控规则、决策流水、看板仍在 member/member-risk-api.js（那是会员域的登录风控）。
 *
 * 🔴 校验都在服务端（灰名单必须有分、三个窗口要亚线性、关键字要过反例集……）——
 * 这里只拦必填，拦不住的让后端报出来，前端不复制一份规则。
 *
 * @Date 2026-10-17
 */
import { postRequest } from '/@/lib/axios';

export const securityApi = {
  // ------------------------------------------------------------ 名单

  /**
   * 名单分页。不传状态只看有效的 —— 客服日常要的是「现在谁被封着」。
   *
   * ⚠️ 身份维度填 `identity`（手机号/邮箱**明文**），服务端算摘要再查；
   * 库里存的是摘要，前端拿不到也算不出。
   */
  queryListPage: (param) => {
    return postRequest('/securityList/queryPage', param);
  },

  /** 新增一条名单 */
  addList: (param) => {
    return postRequest('/securityList/add', param);
  },

  /**
   * 撤销一条名单。**原因必填** —— 「上次为什么解的」和「为什么加的」一样重要。
   * 撤销 IP 黑名单时，网关的处罚键一并删掉。
   */
  revokeList: (param) => {
    return postRequest('/securityList/revoke', param);
  },

  // ------------------------------------------------------------ 限流规则

  /** 整页：规则、网关上报的路由清单（接口覆盖的下拉框）、已发布版本与各实例已加载版本 */
  rateLimitPage: () => {
    return postRequest('/security/rateLimitRule/page');
  },

  /** 改一行的阈值与档位。{ id, values: { burst, limit3m, limit5m, limit10m, longWindow, limitLong, mode } } */
  updateRateLimitRule: (param) => {
    return postRequest('/security/rateLimitRule/update', param);
  },

  /** 加一条按接口的覆盖。{ endpointKey, dimension, values } —— 接口只能从路由清单里选 */
  addRateLimitEndpoint: (param) => {
    return postRequest('/security/rateLimitRule/addEndpoint', param);
  },

  /** 删一条接口覆盖（场景行不能删） */
  deleteRateLimitEndpoint: (param) => {
    return postRequest('/security/rateLimitRule/deleteEndpoint', param);
  },

  // ------------------------------------------------------------ 处罚

  /** 查此刻在罚的东西。{ ip, deviceId, memberId } 至少一个 */
  queryPenalty: (param) => {
    return postRequest('/security/penalty/query', param);
  },

  /** 解除一条处罚。{ key } —— 原样传回查询结果里的那个键 */
  releasePenalty: (param) => {
    return postRequest('/security/penalty/release', param);
  },

  // ------------------------------------------------------------ 蜜罐与关键字

  /** 蜜罐路径、关键字规则与熔断状态 */
  getGate: () => {
    return postRequest('/security/gate/get');
  },

  /** 单独启用 / 停用一条。{ kind: HONEYPOT|KEYWORD, code, enabled } */
  setGateEnabled: (param) => {
    return postRequest('/security/gate/setEnabled', param);
  },

  /** 加一条蜜罐路径。{ code, path } */
  addHoneypot: (param) => {
    return postRequest('/security/gate/addHoneypot', param);
  },

  /** 加一条关键字规则。{ code, scope, pattern } —— 命中反例集（正常输入）的存不进去 */
  addKeyword: (param) => {
    return postRequest('/security/gate/addKeyword', param);
  },

  /** 删一条。{ kind, code } */
  removeGateEntry: (param) => {
    return postRequest('/security/gate/remove', param);
  },

  /**
   * 建蜜罐账号。{ email, placement }
   *
   * 🔴 邮箱只在这一次请求里出现：不进代码、不进种子、留痕里也只记投放位置。
   */
  createCanary: (param) => {
    return postRequest('/security/canary/create', param);
  },

  // ------------------------------------------------------------ 安全事件

  /**
   * 安全事件分页。不传时间兜最近 7 天，一次最多 31 天（按月分区，不带时间会扫所有分区）。
   *
   * 🔴 返回的 matchedSample、userAgent 是**攻击者可控的内容** —— 只能按纯文本渲染。
   */
  queryIncidentPage: (param) => {
    return postRequest('/security/incident/queryPage', param);
  },
};
