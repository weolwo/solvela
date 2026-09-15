package solvela.mall.pay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商城支付通道的开关。
 *
 * <h3>🔴 今天只有一个能用的取值：{@code FAKE}</h3>
 * 全仓<b>没有任何支付网关代码</b>。这个开关存在的意义不是「可以切换」，
 * 而是让「现在用的是假支付」这件事<b>写在配置里、在启动日志里喊出来</b>，
 * 而不是藏在某个 service 的一行 if 里等人发现。
 *
 * <p>形状照抄 {@code VerificationCodeProperties.smsTransport} —— 那边同样是
 * 「本地有个假的实现 + 生产必须换成真的」，而且已经被证明是有效的：
 * 配错了会在<b>启动时</b>炸，不是等第一个真实用户点下去才炸。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "solvela.mall.pay")
public class MallPayProperties {

    /**
     * 支付通道。
     *
     * <h3>🔴 默认是 {@code DISABLED}，最危险的那个值必须<b>显式打开</b></h3>
     * 反过来（默认 {@code FAKE}）的话，任何一个忘了配这一项的环境都会静默拥有
     * 「点一下就算付钱」的能力 —— 而忘配是常态，不是意外。
     *
     * <p>dev / test 的 yaml 里显式写着 {@code FAKE}；生产什么都不配，
     * 于是落在 {@code DISABLED}：服务正常启动，只是那条路明确地关着。
     */
    private Transport transport = Transport.DISABLED;

    public enum Transport {

        /**
         * 支付功能<b>关闭</b>。这是今天生产环境的真实状态 —— 支付网关还没接。
         *
         * <p>{@code POINTS_CASH} 订单会落在待支付、然后被超时 job 取消。
         * 用户点「去支付」得到的是一句「暂未开放」，<b>而不是一个 500</b>：
         * 功能没做和功能坏了对用户是两件事，也该是两种提示。
         */
        DISABLED,

        /**
         * 假支付：点一下就算付了，不动任何真钱。
         *
         * <p>🔴 <b>只允许非生产环境</b>。它等于「任何人都能把自己的订单标成已支付」，
         * 配到生产就是白送商品。
         */
        FAKE,

        /**
         * 真支付。
         *
         * <p>⚠️ <b>今天配这个会启动失败</b> —— 后端一行支付网关代码都没有。
         * 接好网关（回调验签、订单金额核对、幂等落单）之后再放开这个取值，
         * 而放开的那次改动应当<b>同时删掉</b> {@code FAKE} 那条分支。
         */
        REAL
    }
}
