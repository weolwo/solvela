/**
 * 会员实名信息 api 封装
 *
 * 没有 add / update：姓名和身份证是用户提交的，后台改它们没有任何合法用途。
 * 运营在这个页面只做审核（通过 / 驳回）。
 *
 * 🔴 queryPage 与 detail 是两个接口：前者下发脱敏值，后者下发明文。
 * 审核一条时必须看到完整证件号，但那是「一次看一条」的动作 ——
 * 拆开之后「谁能看到完整证件号」才能单独授权、单独审计。
 *
 * @Author:    weolwo
 * @Date:      2026-08-22 21:00:09
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const memberVerifyApi = {
  /**
   * 分页查询（姓名与身份证已脱敏）  @author  weolwo
   */
  queryPage: (param) => {
    return postRequest('/memberVerify/queryPage', param);
  },

  /**
   * 实名详情：返回明文，审核弹窗用  @author  weolwo
   */
  detail: (id) => {
    return getRequest(`/memberVerify/detail/${id}`);
  },

  /**
   * 审核通过  @author  weolwo
   */
  approve: (id) => {
    return getRequest(`/memberVerify/approve/${id}`);
  },

  /**
   * 审核驳回：原因必填，用户在 C 端看到的就是这句话  @author  weolwo
   */
  reject: (param) => {
    return postRequest('/memberVerify/reject', param);
  },
};
