package solvela.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 外部场景的配置：能充多少、最低多少、走不走真的通道。
 *
 * <h3>🔴 为什么是配置而不是一张场景表</h3>
 * 今天只有<b>一个</b>场景（充话费），而它需要的东西就是「几个面额 + 一个下限」。
 * 为此建一张表、一套增删改查、一个管理端页面，是在为还没有的问题付钱。
 *
 * <p>⚠️ 第二个场景进来的时候，这里会变成一个 {@code Map<String, Scene>} ——
 * 那一步是加一层缩进的事。<b>第三个</b>场景进来、或者运营开始要求自助改面额时，
 * 才值得建表。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "solvela.external.recharge")
public class ExternalSceneProperties {

    /**
     * 场景码。券的 {@code scope_refs} 按它匹配 —— 两边写的必须是同一个串，
     * 而那个串在券模板里是一段 json 文本，<b>拼错了不会报错，只是券永远用不了</b>。
     */
    private String sceneCode = "MOBILE_RECHARGE";

    /**
     * 可选面额。
     *
     * <p>🔴 <b>只认这个白名单，不接受任意金额</b>。开放任意金额的话，
     * 一个「充 0.01 元」的请求会把一张满 100 减 10 的券套进一笔一分钱的单子里
     * —— 虽然试算会拦住门槛，但这条路本来就不该存在。
     * 运营商那边也只卖固定面额。
     */
    private List<BigDecimal> faceValues = List.of(
            new BigDecimal("10"), new BigDecimal("30"), new BigDecimal("50"),
            new BigDecimal("100"), new BigDecimal("200"));

    /**
     * 最低充值金额。
     *
     * <p>⚠️ 它和<b>券的门槛</b>（{@code min_amount}）是两回事：
     * 这个是「这个场景最少充多少」，那个是「这张券要满多少才能用」。
     * 两者都拦，而且拦的理由不同 —— 合成一个的话，调整场景下限会悄悄改变
     * 所有券的可用性。
     */
    private BigDecimal minAmount = new BigDecimal("10");

    /** 待支付超时（分钟）。到点由 {@code externalOrderExpire} 取消并放回券 */
    private int payExpireMinutes = 15;

    /**
     * 执行通道。
     *
     * <h3>🔴 默认 {@code DISABLED}，最危险的那个值必须显式打开</h3>
     * 与商城支付那个开关（{@code MallPayProperties}）同一条纪律，理由也一样：
     * 忘配是常态，不是意外。
     */
    private Transport transport = Transport.DISABLED;

    public enum Transport {

        /**
         * 场景关闭。这是今天生产环境的真实状态 —— 运营商接口还没接。
         *
         * <p>下单入口会如实回「暂未开放」，<b>而不是一个 500</b>。
         */
        DISABLED,

        /**
         * 假充值：下单、扣券、标成功，<b>但话费一分钱都不会到账</b>。
         *
         * <p>🔴 <b>只允许非生产环境</b>。配到生产等于「用户花了钱、券也用了，
         * 而系统告诉他充值成功」—— 那是最坏的一种：<b>用户要等到查话费余额才发现</b>，
         * 而那时候券已经核销、单据已经是成功态。
         */
        FAKE,

        /**
         * 真充值。
         *
         * <p>⚠️ <b>今天配这个会启动失败</b> —— 后端一行运营商对接代码都没有。
         */
        REAL
    }
}
