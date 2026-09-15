/**
 * 人工发送站内信 api 封装
 *
 * 补的是管理端一个真实缺口：此前只有「公告」（广播给所有人）和「通知模板」
 * （定义系统触发的措辞），没有「发给某个人」。
 *
 * 🔴 收件人有硬上限（服务端 MANUAL_MAX_RECIPIENTS = 200），超了整批拒绝 ——
 * 这个入口不能变成「用写扩散做广播」的后门。要发给所有人是公告该干的事。
 *
 * @Author:    alaric
 * @Date:      2026-09-15
 * @Copyright  weolwo
 */
import { postRequest } from '/@/lib/axios';

export const manualNotifyApi = {
  /**
   * 发送。
   *
   * @param param.memberIds   会员号数组
   * @param param.memberNames 会员账号数组。查不到会整批拒绝，不是跳过
   * @param param.templateCode 不填默认 MANUAL（自定义文本）
   * @param param.params      渲染参数。MANUAL 要 title 与 content
   * @param param.bizRefId    可空，客服一般填工单号
   * @returns { sent, failed } —— failed 是没收到的会员号，逐个报，不给笼统的「成功」
   */
  send: (param) => {
    return postRequest('/manualNotify/send', param);
  },
};
