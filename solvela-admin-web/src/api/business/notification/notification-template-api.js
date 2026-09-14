/**
 * 通知模板 api 封装
 *
 * 🔴 **这里没有 update，只有 save（新增一版）。不是漏了。**
 *
 * t_member_notification 只存「模板编码 + 版本号 + 参数」，正文是读的时候现渲染的。
 * 原地改模板等于追溯篡改所有历史通知：用户 1 月收到的「恭喜获得 100 积分」，
 * 6 月改完模板就变成另一句话 —— 在金额/奖品类消息上这是事故级的。
 *
 * 所以「编辑」这个动作在页面上也要说成「基于当前版本新建下一版」。
 *
 * @Author:    alaric
 * @Date:      2026-09-14
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const notificationTemplateApi = {
  /** 模板列表，每个编码取最新启用版 */
  list: () => {
    return getRequest('/notificationTemplate/list');
  },

  /** 某个编码的全部历史版本，新的在前 */
  versions: (templateCode) => {
    return getRequest(`/notificationTemplate/versions?templateCode=${encodeURIComponent(templateCode)}`);
  },

  /** 新增一版。版本号由服务端算，前端不要传 */
  save: (param) => {
    return postRequest('/notificationTemplate/save', param);
  },

  /** 停用某一版。🔴 没有删除 —— 删了历史通知就渲染不出来 */
  disable: (templateCode, version) => {
    return postRequest(
      `/notificationTemplate/disable?templateCode=${encodeURIComponent(templateCode)}&version=${version}`,
    );
  },
};
