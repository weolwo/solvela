package solvela.member.api;

import java.math.BigDecimal;

/**
 * 新增资产提案：营销侧发出一笔奖，请会员服务收下。
 *
 * <h3>为什么是同步调用而不是发消息</h3>
 * 调用方需要<b>当场知道结果</b>：被风控拦了、奖品配置有问题、金额非法……
 * 这些原因要立刻落进 {@code t_prize_log.fail_reason}，C 端才能告诉用户为什么没发成，
 * 开发也不用去翻两个服务的日志对时间戳。
 *
 * <p>真正慢的那一段（人工审批、资产入账）不在这个调用里 —— 它由会员服务异步完成后回调。
 *
 * @param sourceBizId       来源单号。<b>跨服务幂等键</b>，与 {@code t_prize_log.external_biz_no} 同值。
 *                          重投必然带来重复请求，会员服务据它判重
 * @param memberId          会员号
 * @param assetType         资产类型 SCORE/BALANCE/COUPON/PHYSICAL
 * @param assetRef          资产引用：券模板、实物 sku 等。值类资产（金额即全部信息）留空
 * @param assetName         资产名称，落在提案上给运营看
 * @param amount            金额/分值
 * @param quantity          数量
 * @param sourceType        来源类型 DRAW/TASK/LOTTERY/MANUAL
 * @param promotionConfigId 优惠配置 id：风控按它算预算与限额
 * @param remark            备注
 */
public record CreateProposalCmd(
        String sourceBizId,
        Long memberId,
        String assetType,
        String assetRef,
        String assetName,
        BigDecimal amount,
        Integer quantity,
        String sourceType,
        Long promotionConfigId,
        String remark) {
}
