package solvela.app.ratelimit;

/**
 * 限流场景。阈值不在这里，在 {@code t_rate_limit_rule} 的场景行里（经快照下发）；这里只定义「有哪些场景、各是什么性质」。
 *
 * <h3>匿名场景</h3>
 * 登录、注册、发码、重置密码四个：此刻还没有登录态，「账号」是请求里填的<b>目标</b>（别人也能填）。
 * 所以这四个场景（网关方案 §2.4、§2.5）：
 * <ul>
 *   <li>账号维度的处罚封顶 30 分钟，老设备豁免 —— 否则限流本身就成了「拿别人的邮箱锁他 8 小时」的手段；</li>
 *   <li>只有它们允许 IP 段维度。</li>
 * </ul>
 * 一个匿名场景里的请求<b>带着登录态</b>时（比如已登录的人去发二次验证码），账号就是他自己的会员号，按登录后的规矩来。
 *
 * <p>⚠️ 枚举名就是规则表 {@code target} 列里的场景编码。改名等于让那几行规则失效（网关会退回内置默认值并 WARN）。
 *
 * @Date 2026-10-17
 */
public enum RateLimitScene {

    LOGIN(true),
    REGISTER(true),
    CODE_SEND(true),
    PASSWORD_RESET(true),

    /** 资产流出：兑换、支付、充值、领奖、抽奖 */
    REDEEM(false),
    SIGN(false),
    CAPTCHA(false),
    DEFAULT_WRITE(false),
    DEFAULT_READ(false),

    /**
     * 不限流，<b>必须写明理由</b>（{@link RateLimit#reason()}）。
     * 目前只有两处：领设备令牌（它的日配额在业务域，而限住它会让其它维度都退化成只剩 IP）、
     * CSP 违规报告（浏览器自动发，限了只会丢报告）。
     */
    EXEMPT(false);

    private final boolean anonymous;

    RateLimitScene(boolean anonymous) {
        this.anonymous = anonymous;
    }

    /** 匿名场景：账号维度是别人能填的目标（见类注释） */
    public boolean anonymous() {
        return anonymous;
    }
}
