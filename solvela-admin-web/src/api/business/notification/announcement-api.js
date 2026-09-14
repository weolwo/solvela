/**
 * 公告 api 封装
 *
 * 公告与模板相反，**可以原地改** —— 正文就存在自己那一行里，
 * 不存在「历史消息指向旧版本」的问题。
 *
 * ⚠️ 但改一条已发布的公告要谨慎：已经读过它的用户不会再收到提醒
 * （游标已经越过它了）。要让所有人重新看到，应该发一条新公告。
 *
 * @Author:    alaric
 * @Date:      2026-09-14
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const announcementApi = {
  list: (limit) => {
    return getRequest(`/announcement/list${limit === undefined ? '' : `?limit=${limit}`}`);
  },

  detail: (id) => {
    return getRequest(`/announcement/detail?id=${id}`);
  },

  save: (param) => {
    return postRequest('/announcement/save', param);
  },

  /** 下架。确认记录还留着 —— 那是合规留痕 */
  offline: (id) => {
    return postRequest(`/announcement/offline?id=${id}`);
  },

  /** 🔴 物理删除，连同确认记录。合规留痕会一起没，一般用 offline 就够了 */
  delete: (id) => {
    return postRequest(`/announcement/delete?id=${id}`);
  },

  /**
   * 强制确认公告的确认人数。
   *
   * ⚠️ 只有分子。分母（当前命中人群的会员数）是**动态的** —— 新注册用户会进来，
   * 所以覆盖率不会停在 100%。这句话要写在页面上，否则运营会以为「覆盖率掉了」。
   */
  ackCount: (id) => {
    return getRequest(`/announcement/ackCount?id=${id}`);
  },
};
