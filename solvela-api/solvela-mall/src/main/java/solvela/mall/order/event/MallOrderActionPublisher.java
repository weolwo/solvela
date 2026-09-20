package solvela.mall.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.event.BizEventPublisher;
import solvela.event.BizActionCodes;
import solvela.event.BizActionEvent;
import solvela.mall.MallOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「这一单付掉了」的<b>唯一广播处</b>。
 *
 * <h3>🔴 为什么要单独一个类，而不是在两个 service 里各写一遍</h3>
 * 因为商城有<b>两条</b>走到「已付」的路，而它们长得完全不一样：
 * <table border="1">
 *   <tr><th>路径</th><th>入口</th><th>说明</th></tr>
 *   <tr><td>混合单（积分 + 现金）</td><td>{@code MallPayService.pay}</td>
 *       <td>落单时是 0-待支付，靠支付确认推到 10</td></tr>
 *   <tr><td><b>纯积分单</b></td><td>{@code MallRedeemService.redeem}</td>
 *       <td><b>不经过支付</b>：落单那一刻资产就结清，直接是 10-待履约</td></tr>
 * </table>
 *
 * <p>只埋前一条的表现是 —— <b>纯积分兑换不算任务进度</b>。而纯积分单恰恰是这个平台
 * 最主要的兑换方式，所以实际效果是「订单类任务基本不动」：
 * 它不报错、不打日志，只会变成「为什么我兑换了任务没进度」的客诉。
 *
 * <p>这个岔路口已经漏过一次：优惠券方案 §11.8 记着，阶段 4 少的正是
 * 「纯积分单在落单那一刻资产就结清」这一半。把翻译收在一个类里，
 * 是为了让下一个人加第三条路径时至少能搜到这里。
 *
 * <h3>🔴 本类<b>不</b>决定"算多少"</h3>
 * 它把 {@code payPoints} / {@code payCash} <b>都</b>放进 payload，
 * 由 {@code t_task_event.metric_source} 去挑用哪一个。
 * 刻意<b>不</b>设 {@code BizActionEvent.amount} —— 一旦设了，
 * {@code TaskEventService.resolveAmount} 会优先用它，注册表里的
 * {@code metric_source} 就再也不起作用了。
 *
 * <p>换句话说：运营想把「累计消费」从积分口径改成现金口径，
 * 改的是<b>后台一行数据</b>，不是这个类。这正是 {@code payload_schema}
 * 那一列存在的意义。
 *
 * @author alaric
 * @date 2026-09-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MallOrderActionPublisher {

    private final BizEventPublisher bizEventPublisher;

    /**
     * 广播一笔订单已完成付款。
     *
     * <p>🔴 <b>必须在业务事务内调用</b>：接住它的监听器挂在 AFTER_COMMIT 上，
     * 事务回滚时不会投递 —— 所以不存在「单没付成但任务已经发了奖」。
     * 挪到事务外会立刻投递（监听器开了 fallbackExecution），那正是上面这句失效的方式。
     *
     * <p>一次发<b>两条</b>：计次的 {@link BizActionCodes#ORDER_PAID} 和计额的
     * {@link BizActionCodes#ORDER_AMOUNT}。分成两个编码不是冗余 ——
     * 它们的 {@code metric_source} 不同（NONE / payPoints），
     * 「下单 3 次」和「累计消费 500 分」是两类任务，各订阅各的。
     * 没有任何任务订阅其中一条时，防腐层会安静地忽略它。
     *
     * <p>幂等键两条都用<b>订单号</b>：同一笔单无论被重推多少次，
     * {@code t_task_record_flow} 上的 {@code uk_t_tsk_flw_evt} 都会挡下重复计数。
     * 这也是反查对账 job 敢直接补推的前提。
     */
    public void publishOrderPaid(MallOrder order) {
        if (order == null || order.getMemberId() == null || order.getOrderNo() == null) {
            // 走不到；真走到了说明调用方拿了个半成品订单，别把它当成一次真实付款广播出去
            log.warn("【商城打点】订单信息不完整，本次不广播付款动作。order={}", order);
            return;
        }

        Map<String, Object> payload = payloadOf(order);
        /*
         * 发生时间取"现在"而不是 order.getPayTime()：
         * 调用方手里的 order 是【标记已付之前】读出来的，pay_time 那一列
         * 此刻还是旧值（多半是 null）。传 null 下游会用数据库时钟兜底，
         * 结果一样，但依赖兜底和明确传值是两回事 —— 后者在跨零点时才说得清。
         */
        LocalDateTime now = LocalDateTime.now();

        bizEventPublisher.publish(new BizActionEvent(
                BizActionCodes.ORDER_PAID, order.getMemberId(), order.getOrderNo(),
                null, now, payload));
        bizEventPublisher.publish(new BizActionEvent(
                BizActionCodes.ORDER_AMOUNT, order.getMemberId(), order.getOrderNo(),
                null, now, payload));
    }

    /**
     * 事件原文。两条事件共用一份 —— 它落进 {@code t_task_record_flow.event_payload}，
     * 是客诉复盘时唯一能回答「这一单当时到底是什么样」的东西。
     *
     * <p>用 {@link LinkedHashMap} 而不是 {@code Map.of}：后者<b>不允许 null 值</b>，
     * 而 {@code payCash} 在纯积分单上完全可能是 null（DDL 允许）。
     * 顺序稳定也让日志和流水读起来一致。
     *
     * <h3>🔴 public static 是给对账补推用的，不是图方便</h3>
     * {@code MallOrderAuditProvider} 补推一条漏掉的动作时，必须产出<b>字节级相同</b>
     * 的 payload —— 计额型任务按 {@code metric_source} 从这里取数，
     * 两份实现哪怕只差一个键名，补推出来的进度就和正常链路对不上，
     * 而且只会在对账之后才暴露。所以这里只能有一份实现。
     */
    public static Map<String, Object> payloadOf(MallOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        // 两个口径都给，由 metric_source 挑 —— 见类注释
        payload.put("payPoints", order.getPayPoints() == null ? 0 : order.getPayPoints());
        payload.put("payCash", order.getPayCash() == null ? BigDecimal.ZERO : order.getPayCash());
        payload.put("quantity", order.getQuantity());
        // 商品维度：「买够 3 件 X」这类任务将来靠它做条件，今天没人用也先带上 ——
        // 事后补字段要改代码，而 payload 里多一个键不花什么
        payload.put("commodityCode", order.getCommodityCode());
        payload.put("skuCode", order.getSkuCode());
        return payload;
    }
}
