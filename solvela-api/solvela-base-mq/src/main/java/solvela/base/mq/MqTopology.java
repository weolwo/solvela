package solvela.base.mq;

/**
 * 交换机、队列、路由键的<b>唯一定义处</b>。
 *
 * <h3>为什么是常量而不是各自写字面量</h3>
 * 发消息的在营销服务，收消息的在会员服务，两边是两个进程、两份代码。
 * 名字写成字面量的话，改一个字母不会有任何编译错误 ——
 * 表现是<b>消息发出去了、没人收</b>，而这两侧的日志都显示"正常"。
 * 放一个常量类在两边都依赖的模块里，这类问题在编译期就没有了。
 */
public final class MqTopology {

    /** 发奖派发的交换机。topic 类型：将来加「彩票开奖」「任务达标」等路由键不用改声明 */
    public static final String PRIZE_EXCHANGE = "solvela.prize";

    /** 中奖待派发的路由键 */
    public static final String PRIZE_DISPATCH_ROUTING_KEY = "prize.dispatch";

    /** 会员服务的派发队列 */
    public static final String PRIZE_DISPATCH_QUEUE = "solvela.prize.dispatch";

    /**
     * 死信交换机与队列。
     *
     * <p>🔴 <b>必须有</b>：派发失败的消息如果直接丢掉，等于奖没发出去而且没人知道。
     * 进死信队列至少留下了证据，运维能看见、能重投。
     * 没有死信配置时 RabbitMQ 的默认行为是<b>直接丢弃</b>被拒绝的消息。
     */
    public static final String DLX_EXCHANGE = "solvela.prize.dlx";

    public static final String PRIZE_DISPATCH_DLQ = "solvela.prize.dispatch.dlq";

    private MqTopology() {
    }
}
