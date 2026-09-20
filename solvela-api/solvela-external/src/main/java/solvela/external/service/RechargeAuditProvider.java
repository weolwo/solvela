package solvela.external.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.event.BizActionCodes;
import solvela.external.ExternalOrder;
import solvela.external.dao.ExternalOrderDao;
import solvela.marketing.api.BizActionAuditProvider;
import solvela.marketing.api.BizActionRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部场景侧的<b>事后清单</b>：窗口内哪些消费真的成功了。
 *
 * <p>与 {@code MallOrderAuditProvider} 同一个形状，理由见
 * {@link BizActionAuditProvider} 的类注释：本域没法知道自己漏了谁
 * （答案在任务域的表里），也不该认识任务引擎
 * （{@code ExternalPlayBoundaryTest} 扫本模块字节码守着）。
 * 所以方向是任务域来拉，本域只如实回答。
 *
 * @author alaric
 * @date 2026-09-17
 */
@Component
@RequiredArgsConstructor
public class RechargeAuditProvider implements BizActionAuditProvider {

    private final ExternalOrderDao externalOrderDao;

    @Override
    public String supportActionCode() {
        return BizActionCodes.RECHARGE_PAID;
    }

    @Override
    public List<BizActionRecord> listSettled(LocalDateTime from, LocalDateTime to, int limit) {
        return externalOrderDao.selectSucceededBetween(from, to, limit).stream()
                .map(RechargeAuditProvider::toRecord)
                .toList();
    }

    /**
     * 🔴 每个字段都要和 {@code ExternalRechargeService.payAndExecute} 里那次打点对齐：
     * <ul>
     *   <li>{@code bizId} 是外部消费单号 —— 幂等键，对不上就挡不住重复计数；</li>
     *   <li>{@code occurredAt} 用 {@code finish_time}（markSuccess 写的那一刻），
     *       不是"现在" —— 传现在会把昨晚漏掉的那笔记进今天的周期；</li>
     *   <li>payload 的键名逐个对齐，计额型任务按 {@code metric_source} 从这里取数。</li>
     * </ul>
     *
     * <p>⚠️ 这份 payload 和打点那处是<b>两份代码</b>，不像商城那边能共用一个
     * {@code payloadOf}。本域的打点只有一处、就在同一个包里，为此单开一个类不划算 ——
     * 但代价是<b>改一处必须改两处</b>。真到了要加第三个字段的时候，
     * 先把它们抽成一个方法再改。
     */
    private static BizActionRecord toRecord(ExternalOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("sceneCode", order.getSceneCode());
        payload.put("payAmount", order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount());
        payload.put("originalAmount",
                order.getOriginalAmount() == null ? BigDecimal.ZERO : order.getOriginalAmount());
        return new BizActionRecord(
                order.getOrderNo(), order.getMemberId(), order.getFinishTime(), payload);
    }
}
