/**
 * 提案表 api 封装
 *
 * @Author:    weolwo
 * @Date:      2026-04-18 23:13:50
 * @Copyright  weolwo
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const proposalRecordApi = {

  /**
   * 分页查询  @author  weolwo
   */
  queryPage : (param) => {
    return postRequest('/proposalRecord/queryPage', param);
  },

  /**
   * 提案漏斗：到账率、审批积压、下发卡单、资产/来源分布与流程体检。
   * ⚠️ 服务端刻意忽略 status 筛选项 —— 它是漏斗要拆解的维度，跟着筛会让到账率恒为 100%；
   * assetType / sourceType / 时间范围这些切片维度照常生效  @author  alaric
   */
  funnel: (param) => {
    return postRequest('/proposalRecord/funnel', param);
  },

  /*
   * 不再封装增删改 —— 后端对应的四个接口已整组移除（v3.69.0）。
   *
   * 提案表是「钱出去的必经之路」，也是账务对账的主线：
   *   · 「新建」会造出 status=0 的提案。正常链路只落 10-待一审 / 30-待执行 / 80-风控拦截，
   *     0 是绕过风控与预算直接插库的产物，不会被任何流程推进，也没人会发现 ——
   *     漏斗里那条「有 N 条提案停在等待中」的告警，来源就是这个按钮；
   *   · delete 是物理删除，而 t_physical_delivery.proposal_id 与
   *     t_member_asset_transaction.biz_ref_id 都指着它，删掉提案，
   *     下游的履约单和资金流水就成了无源之水，出账查不到依据。
   *
   * 提案只能由发奖链路创建，由审批（approve / reject）与下发推进状态。
   */
};
