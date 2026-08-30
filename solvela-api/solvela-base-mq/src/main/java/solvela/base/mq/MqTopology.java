package solvela.base.mq;

/**
 * 交换机、队列、路由键的<b>唯一定义处</b>。
 *
 * <h3>为什么是常量而不是各自写字面量</h3>
 * 发消息的在会员服务，收消息的在营销服务，两边是两个进程、两份代码。
 * 名字写成字面量的话，改一个字母不会有任何编译错误 ——
 * 表现是<b>消息发出去了、没人收</b>，而两侧日志都显示"正常"。
 *
 * <h3>🔴 消息只有一个方向：member → marketing</h3>
 * 反方向（营销把奖交给会员）走的是<b>同步 HTTP</b>（{@code MemberProposalApi}），
 * 因为调用方需要当场拿到「被拒的原因」落进发奖流水。
 * 只有「资产真正入账」这件事是慢的（可能人工审批几小时），才必须异步。
 *
 * <p>曾经这里有一组 marketing → member 的派发队列，随同步方案落地后已删除 ——
 * 留着一组没人用的拓扑，下一个人会以为发奖是走消息的。
 */
public final class MqTopology {

    /** 发奖相关的交换机。topic 类型：将来加活动事件（会员登录、下单）不用改声明 */
    public static final String PRIZE_EXCHANGE = "solvela.prize";

    /** 资产入账结果回写的路由键 */
    public static final String DISPATCH_RESULT_ROUTING_KEY = "prize.dispatch.result";

    /** 营销服务消费的回写队列 */
    public static final String DISPATCH_RESULT_QUEUE = "solvela.prize.dispatch.result";

    /**
     * 死信交换机与队列。
     *
     * <p>🔴 <b>必须有</b>：回写失败的消息若被直接丢掉，发奖流水会永远停在「已受理」，
     * 而用户其实早就收到或没收到了 —— 且没有任何地方记得这件事。
     * 没有死信配置时 RabbitMQ 对被拒消息的默认行为就是<b>直接丢弃</b>。
     */
    public static final String DLX_EXCHANGE = "solvela.prize.dlx";

    public static final String DISPATCH_RESULT_DLQ = "solvela.prize.dispatch.result.dlq";

    /**
     * 回写消息的消费者标识，落进 {@code t_mq_message_log.consumer_key}。
     *
     * <p>固定消费者用 handler 名；活动事件那类消息填的是活动编码。
     * 同一张表两种消费者，靠这一列隔离重试。
     */
    public static final String DISPATCH_RESULT_CONSUMER = "prizeDispatchResultListener";

    private MqTopology() {
    }
}
