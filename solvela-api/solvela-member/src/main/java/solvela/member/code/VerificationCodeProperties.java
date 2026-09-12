package solvela.member.code;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 会员验证码的参数。<b>邮箱与短信共用</b> —— 除了 IP 日限（两条通道的成本差着量级）。
 *
 * <pre>
 * solvela:
 *   member:
 *     code:
 *       length: 6
 *       ttl: 5m
 *       resend-cooldown: 60s
 *       max-verify-attempts: 5
 *       max-send-per-email-per-day: 10
 *       max-send-per-ip-per-day: 20
 * </pre>
 *
 * @Date 2026-09-09
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.code")
public class VerificationCodeProperties {

    /**
     * 验证码位数。
     *
     * <p>🔴 <b>6 位，不是管理端那套的 4 位。</b>那边的注释写着「5 分钟有效期内穷举 1 万种也够呛」——
     * 这句话对<b>人</b>成立，对脚本不成立：1 万种组合、无限次尝试，几秒钟就跑完了。
     *
     * <p>位数与 {@link #maxVerifyAttempts} 是<b>一对</b>，要一起看：
     * 6 位 = 100 万种，配 5 次上限，猜中的概率是二十万分之一。
     * 单调其中一个都会让这个数失去意义。
     */
    private int length = 6;

    /**
     * 有效期。太短用户来不及切到邮箱，太长等于给暴力破解更多时间。
     *
     * <p>邮件比短信慢（要过对方服务器的反垃圾），5 分钟是能接受的下限。
     */
    private Duration ttl = Duration.ofMinutes(5);

    /**
     * 重发冷却。挡的是连点，也顺带挡住把这个接口当<b>免费邮件发射器</b>用。
     */
    private Duration resendCooldown = Duration.ofSeconds(60);

    /**
     * 同一个验证码最多能验错几次，用尽即作废、必须重发。
     *
     * <p>见 {@link #length}：这两个数是一对。
     * 「作废」而不是「锁一会儿」是有意的 —— 锁定会让攻击者拿到一个
     * 「这个邮箱刚才发过码」的信号，而重发的成本本来就很低。
     */
    private int maxVerifyAttempts = 5;

    /**
     * 同一个目标（邮箱或手机号）一天最多收几条。
     *
     * <p>不限量的后果不只是骚扰：发件人显示的是我们的域名，被拿去发垃圾之后
     * <b>整个域名进黑名单</b>，此后所有系统邮件（含管理端的登录验证码）都进垃圾箱。
     * 那是个要几周才能洗白的坑。
     */
    private int maxSendPerTargetPerDay = 10;

    /**
     * 同一 IP 一天最多发几封。
     *
     * <p>与上一条是<b>两个维度</b>：按邮箱限挡的是「盯着一个人发」，
     * 按 IP 限挡的是「拿一堆邮箱地址群发」。只做前者的话，
     * 一个脚本换着邮箱发，每个都不超限，而总量已经足够让域名被拉黑。
     */
    private int maxSendPerIpPerDay = 20;

    /**
     * 同一 IP 一天最多发几条<b>短信</b>。
     *
     * <p>🔴 比邮件那档紧得多，因为<b>成本差着量级</b>：邮件不要钱，
     * 短信一条几分钱 —— 被刷一天就是实打实的账单，而且短信厂商那边
     * 也有自己的风控，量一大整个签名会被限。
     *
     * <p>邮件被刷的后果是域名进黑名单（要几周洗白），短信被刷的后果是当场花钱。
     * 两者都不好，但一个能事后补救、一个不能。
     */
    private int maxSmsSendPerIpPerDay = 5;

    /** 按通道取 IP 日限。 */
    public int maxSendPerIpPerDay(String channel) {
        return "sms".equals(channel) ? maxSmsSendPerIpPerDay : maxSendPerIpPerDay;
    }

    /**
     * <b>邮件</b>怎么送到用户手上。
     *
     * <p>🔴 与 {@code CodeDelivery} 是<b>两个正交的东西</b>，别混：
     * <ul>
     *   <li>{@code CodeDelivery} 回答「这条码该不该送出去」—— 那是<b>安全</b>决定
     *       （送给一个没有账号的目标等于泄露账号是否存在）；</li>
     *   <li>本项回答「送的时候走什么通道」—— 那是<b>环境</b>决定。</li>
     * </ul>
     * 合成一个枚举的话，一个为了本地调试加的取值就会出现在安全判断的 switch 里。
     */
    private Transport emailTransport = Transport.REAL;

    /**
     * <b>短信</b>怎么送到用户手上。
     *
     * <p>⚠️ 默认 REAL，而<b>全仓还没有接任何短信服务商</b> —— 也就是说
     * 生产环境走这条路会发送失败。这是刻意的：默认值应当描述「正确的世界」，
     * 而不是迁就当前的缺口。真要上线手机号验证码，先接厂商。
     *
     * <p>dev / test 配 LOG，不用等厂商也能把整条链路跑通。
     */
    private Transport smsTransport = Transport.REAL;

    /**
     * <b>明知没接短信厂商，仍然允许生产环境启动。</b>默认 false。
     *
     * <h3>这不是降级开关，是一句「我知道」</h3>
     * {@code MemberSmsCodeService} 的启动闸拦的是<b>失手</b>：配了 REAL 却没接厂商，
     * 不拦的话要等第一个真实用户点「获取验证码」才暴露，表现是一个 500，
     * 而那一刻没人在看日志。
     *
     * <p>但「这个站暂时只用邮箱」是一个<b>合法的部署形态</b> ——
     * 邮箱注册 / 登录 / 找回密码都是完整的。原来的闸没给这种形态留出口，
     * 于是只能靠改代码或改环境名绕过去，那比留一个开关糟糕得多。
     *
     * <p>🔴 <b>开了它，短信这条路仍然是一堵墙</b>（见 {@link
     * solvela.member.sms.UnavailableSmsSender}）：任何真的走到发短信那一步的请求
     * 都会抛异常、对用户是 500。所以开这个开关时<b>必须同时保证 C 端不提供手机号入口</b>，
     * 否则你只是把「启动失败」换成了「用户点一下报 500」—— 后者更难发现。
     *
     * <p>每次启动都会打一条 WARN，刻意的：临时开关最容易变成永久状态，
     * 而唯一能防住这件事的是让它在每份启动日志里都刺眼。
     */
    private boolean allowMissingSmsVendor = false;

    /** 送达通道。 */
    public enum Transport {

        /** 真发出去。邮件走 MailService，短信走厂商。 */
        REAL,

        /**
         * <b>不发，把验证码打进日志。</b>本地开发与联调用，省掉配 SMTP / 接短信厂商这一步。
         *
         * <p>🔴 <b>生产环境用它会启动失败</b>，这是刻意的：日志里躺着每个人的验证码，
         * 拿到日志（或 ELK 权限）就能接管任意账号 —— 而日志的访问面通常比数据库宽得多，
         * 还会被采集、被转发、被长期保留。
         *
         * <p>做成「启动即失败」而不是「静默降级成 REAL」：降级的话，
         * 有人在生产配了 LOG 却什么都没发生，他会以为这个开关不生效，
         * 转头去别处找原因 —— 而真正的问题（配置文件里躺着一个危险开关）没人知道。
         */
        LOG,
    }

    private Duration dailyWindow = Duration.ofDays(1);

    public Duration ttl() {
        return ttl == null ? Duration.ofMinutes(5) : ttl;
    }

    public Duration resendCooldown() {
        return resendCooldown == null ? Duration.ofSeconds(60) : resendCooldown;
    }

    public Duration dailyWindow() {
        return dailyWindow == null ? Duration.ofDays(1) : dailyWindow;
    }

    public int length() {
        return length < 4 ? 6 : length;
    }

    public int maxVerifyAttempts() {
        return maxVerifyAttempts <= 0 ? 5 : maxVerifyAttempts;
    }
}
