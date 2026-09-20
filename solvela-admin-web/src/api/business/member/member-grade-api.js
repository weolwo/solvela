/**
 * 会员等级 api 封装
 *
 * 🔴 没有 delete：等级配置删不得。删掉一档之后，正停在那一档上的会员会挂在一个
 *    配置里已经不存在的等级号上 —— 页面显示不出名字，判级也解释不了。
 *    要下线一档就停用它，让期末结算把人自然地重判下来。
 *
 * @Author:    alaric
 * @Date:      2026-09-18
 */
import { getRequest, postRequest } from '/@/lib/axios';

export const memberGradeApi = {
  // ---------------- 等级配置 ----------------

  /** 全部等级（含停用），按等级升序。下拉与列表共用  @author alaric */
  listConfig: () => {
    return getRequest('/memberGrade/config/list');
  },

  /** 新增 / 编辑一档  @author alaric */
  saveConfig: (param) => {
    return postRequest('/memberGrade/config/save', param);
  },

  /** 启用 / 停用一档  @author alaric */
  updateConfigStatus: (id, status) => {
    return getRequest(`/memberGrade/config/updateStatus?id=${id}&status=${status}`);
  },

  // ---------------- 会员成长值 ----------------

  /** 会员成长值分页  @author alaric */
  queryGrowthPage: (param) => {
    return postRequest('/memberGrade/growth/queryPage', param);
  },

  /**
   * 单个会员的成长值现状。
   * ⚠️ 从没拿到过成长值的会员返回 null —— 那是「还没参与过」，不是「查不到这个人」
   * @author alaric
   */
  getGrowth: (memberId) => {
    return getRequest(`/memberGrade/growth/${memberId}`);
  },

  /** 成长值流水分页。memberId 必填  @author alaric */
  queryGrowthLogPage: (param) => {
    return postRequest('/memberGrade/growthLog/queryPage', param);
  },

  /** 等级变更留痕分页  @author alaric */
  queryGradeLogPage: (param) => {
    return postRequest('/memberGrade/gradeLog/queryPage', param);
  },

  /** 人工调级。原因必填  @author alaric */
  adjustGrade: (param) => {
    return postRequest('/memberGrade/adjust', param);
  },
};
