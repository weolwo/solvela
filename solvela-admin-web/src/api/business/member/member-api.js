/**
 * 会员主表 api 封装
 *
 * 没有 add / update：会员是 C 端自己注册出来的，后台凭空造一个会员会绕过注册链路的
 * 手机号校验、发号器、钱包初始化 —— 造出来的是一个数据不全、登录不了的壳。
 * 后台能改的只有状态（风控动作）和运营备注，各走一个窄接口。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 19:39:08
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const memberApi = {
  /**
   * 分页查询  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/member/queryPage', param);
  },

  /**
   * 冻结 / 解冻。已注销是终态，服务端会拒绝把它改回来  @author  weolwo
   */
  updateStatus: (memberId, status) => {
    return getRequest(`/member/updateStatus/${memberId}/${status}`);
  },

  /**
   * 保存运营备注。走 body 而不是路径参数 —— 备注是自由文本，
   * 可能带斜杠井号中文，放 URL 上要处理一堆转义，而且会被完整记进 access log  @author  weolwo
   */
  updateRemark: (param) => {
    return postRequest('/member/updateRemark', param);
  },
};
