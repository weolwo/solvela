package solvela.member.api;

import java.math.BigDecimal;

/**
 * 扣减 / 退还一笔资产。
 *
 * <h3>🔴 幂等靠 bizRefId，不靠调用方自己去重</h3>
 * {@code t_member_asset_transaction} 上有 {@code UNIQUE(biz_ref_id, asset_type)}，
 * 所以同一个 {@code bizRefId} 重复提交会被数据库挡住 ——
 * mall.sql 里明写「下单时把 biz_ref_id 传 order_no，<b>重复扣款天然幂等，
 * 不要另造去重表</b>」。
 *
 * @param memberId  会员号，<b>由调用方从登录态取</b>
 * @param assetType 资产类型，取值对齐 {@code PrizeTypeEnum}（SCORE / BALANCE …）。
 *                  用字符串而不是枚举：这个契约将来要跨进程，
 *                  而枚举值域的变更在两边不是同时发版的
 * @param amount    金额，必须 &gt; 0。<b>退还也传正数</b> —— 方向由调的哪个方法决定，
 *                  不是由正负号决定：负号在日志里太容易看漏
 * @param bizType   业务类型，落进流水做归因，如 {@code MALL_EXCHANGE}
 * @param bizRefId  业务单号，<b>幂等键</b>。商城传订单号
 * @param remark    流水备注，会被运营在后台看到
 */
public record AssetDebitCmd(
        Long memberId,
        String assetType,
        BigDecimal amount,
        String bizType,
        String bizRefId,
        String remark) {
}
