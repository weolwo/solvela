package solvela.ledger.wallet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.event.BizEventPublisher;
import solvela.enums.PrizeTypeEnum;
import solvela.enums.TransactionTypeEnum;
import solvela.event.BizActionCodes;
import solvela.event.BizActionEvent;
import solvela.ledger.MemberAssetTransaction;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「积分入账了」的<b>唯一广播处</b>。
 *
 * <h3>🔴 为什么要单独一个类，而不是在每个入账点各写一遍</h3>
 * 因为 {@code MemberWalletService} 里写 {@code INCOME} 流水的<b>不止一处</b>：
 * <table border="1">
 *   <tr><th>方法</th><th>谁在调</th><th>语义</th></tr>
 *   <tr><td>{@code executeWalletCharge}</td><td>{@code WalletChargeHandler}</td><td>发奖入账</td></tr>
 *   <tr><td rowspan="2">{@code executeWalletRefund}</td>
 *       <td>{@code AssetGrantApiService.grantWallet}</td><td><b>商城/发放入账</b></td></tr>
 *   <tr><td>{@code AssetDebitApiService.refund}</td><td><b>真正的退回</b></td></tr>
 * </table>
 *
 * <p>各写一遍的后果是漏掉其中一个，而那是 {@code ORDER_PAID} 漏掉纯积分单的同一个形状
 * —— 不报错、不打日志，只表现为「有些人的成长值莫名其妙比别人少」。
 *
 * <h3>🔴 本类<b>不</b>判「算不算成长值」</h3>
 * 它广播全部 SCORE 入账，{@code bizType} 原样带上。
 * 哪些算是<b>会员域</b>的白名单，理由见 {@link BizActionCodes#SCORE_EARNED}。
 *
 * <p>⚠️ 特别注意<b>不能按方法名判</b>：{@code executeWalletRefund} 的名字叫「退回」，
 * 但它的 javadoc 自己写着「复用……（通用退还/入账）」—— 商城发积分走的就是它。
 * 按方法跳过它，会把商城发的积分一起漏掉。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreIncomeActionPublisher {

    private final BizEventPublisher bizEventPublisher;

    /**
     * 一条资产流水刚落库，看看要不要广播。
     *
     * <p>🔴 <b>必须在写流水的那个事务内调用</b>：接住它的监听器挂在 AFTER_COMMIT 上，
     * 入账事务回滚时就不会投递 —— 不存在「积分没到账但成长值涨了」。
     *
     * <p>只广播 <b>SCORE 的收入</b>：
     * <ul>
     *   <li>BALANCE（现金）不算成长值 —— 它是另一种资产，口径不同；</li>
     *   <li>支出当然不算 —— 花钱不该掉等级，那正是成长值独立于余额的理由。</li>
     * </ul>
     */
    public void publishIfScoreIncome(MemberAssetTransaction txn) {
        if (txn == null
                || !PrizeTypeEnum.SCORE.name().equals(txn.getAssetType())
                || txn.getTransactionType() != TransactionTypeEnum.INCOME) {
            return;
        }
        if (txn.getMemberId() == null || txn.getChangeAmount() == null) {
            // 走不到；真走到了说明流水是半成品，别把它当成一次真实入账广播出去
            log.warn("【积分入账打点】流水信息不完整，本次不广播。txn={}", txn);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        // 🔴 会员域的白名单判据就是它，不能不带
        payload.put("bizType", txn.getBizType());
        payload.put("bizRefId", txn.getBizRefId());
        payload.put("amount", txn.getChangeAmount());

        bizEventPublisher.publish(new BizActionEvent(
                BizActionCodes.SCORE_EARNED,
                txn.getMemberId(),
                // 幂等键用流水的业务单号 —— t_member_asset_transaction 上
                // UNIQUE(biz_ref_id, asset_type) 保证了同一个单号只会入账一次
                txn.getBizRefId(),
                txn.getChangeAmount(),
                LocalDateTime.now(),
                payload));
    }
}
