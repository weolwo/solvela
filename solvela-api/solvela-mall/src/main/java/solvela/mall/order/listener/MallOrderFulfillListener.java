package solvela.mall.order.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import solvela.mall.order.event.MallOrderPendingEvent;
import solvela.mall.order.service.MallFulfillService;

/**
 * 兑换事务<b>提交之后</b>发起履约。
 *
 * <h3>为什么是 AFTER_COMMIT 而不是在兑换方法里直接调</h3>
 * 直接调的话，发货单/券会跟着兑换事务一起回滚或一起提交 ——
 * 听起来更「原子」，实际是把一次外部调用塞进了付款事务：
 * 发券那一步抖一下，用户看到的是「兑换失败」，而他的积分本该扣、东西本该给。
 * 分开之后失败只影响履约（停在 60 等重发），付款那一半已经是既成事实。
 *
 * <h3>🔴 这个类必须独立于 MallFulfillService</h3>
 * 铁律 11：{@code @Transactional} 靠代理生效，同一个 Bean 内部自调用绕过代理，
 * <b>事务静默不开启</b>。监听器和被调的事务方法放一起，那个 REQUIRES_NEW 就是摆设。
 *
 * <h3>不用线程池，是权衡过的</h3>
 * {@code GlobalEventDispatcher} 把派发丢进线程池，因为那条链路压测掉了六成 QPS。
 * 履约这边是一到两条 INSERT，同步做的代价是几毫秒；
 * 换来的是<b>失败仍留在请求线程的调用栈里</b> —— 进了池之后异常不再上抛，
 * 只能靠日志和下游表自查发现问题（那个类的注释末尾自己写了这一点）。
 *
 * <p>该改成异步的时点很明确：<b>资产域独立成服务的那天</b>。
 * 那时 {@code AssetGrantApi} 变成一次 HTTP 往返，同步等于让用户的响应
 * 挂在别人的网络上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MallOrderFulfillListener {

    private final MallFulfillService mallFulfillService;

    /**
     * 🔴 <b>这里必须把异常全吃掉。</b>
     *
     * <p>此刻兑换事务<b>已经提交</b>：积分扣了、订单落了。异常再往上抛，
     * 用户收到的是 500，而他会以为兑换失败 —— 然后再兑一次。
     * 而 {@link MallFulfillService#fulfill} 是 REQUIRES_NEW，
     * 它自己那半会独立回滚，单子退回「待履约」，重发是安全的。
     *
     * <p>所以这里 catch 的边界是刻意画在<b>事务之外</b>的：
     * 吃掉异常不会掩盖脏数据，只会把「履约没成」变成一条日志。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPending(MallOrderPendingEvent event) {
        try {
            mallFulfillService.fulfill(event.orderNo());
        } catch (Exception e) {
            // 单子还在「待履约」，等重发。这条日志是今天唯一的发现途径
            log.error("【商城履约】{} 履约异常，单子停在待履约等重发", event.orderNo(), e);
        }
    }
}
