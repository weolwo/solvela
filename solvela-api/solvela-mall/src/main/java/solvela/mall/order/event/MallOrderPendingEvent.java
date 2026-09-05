package solvela.mall.order.event;

/**
 * 订单进入<b>待履约</b>。
 *
 * <p>在兑换事务里发布，由 {@code @TransactionalEventListener(AFTER_COMMIT)} 在
 * <b>提交之后</b>投递 —— 这样「事务回滚了但货已经发出去」不会发生。
 *
 * <h3>为什么只带订单号</h3>
 * 事件是<b>通知</b>，不是数据传输。带上整个订单快照的话，
 * 履约那边就有两个信息源（事件里的和库里的），而它们在重试路径上必然不一致：
 * 重试是从库里捞单子的，事件早没了。<b>只有一个源，就不会有不一致。</b>
 */
public record MallOrderPendingEvent(String orderNo) {
}
