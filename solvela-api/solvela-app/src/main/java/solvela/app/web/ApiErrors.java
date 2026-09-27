package solvela.app.web;

import org.springframework.http.HttpStatus;

/**
 * C 端的错误码表。<b>一个错误 = 一个 HTTP 状态 + 一个稳定的字符串码 + 一句人话。</b>
 *
 * <h3>为什么不是数字码</h3>
 * {@code 30001} 这样的数字，客户端要对着文档翻，日志里看到也认不出。
 * 字符串码自解释：告警里出现 {@code ACCOUNT_LOCKED} 不用查表就知道发生了什么。
 * 代价是多几个字节，而这是 C 端唯一不用省的东西。
 *
 * <h3>为什么每个错误自带 HTTP 状态</h3>
 * 上一版所有响应都是 200，业务失败靠 body 里的 code 区分。后果是具体的：
 * 网关按状态码做的熔断和限流全部失效，APM 面板上成功率永远 100%，
 * 客户端的 HTTP 库无法在拦截器里统一处理 401，重试策略也没法写
 * （一个 200 到底该不该重试？）。
 *
 * <p>状态码是给<b>基础设施</b>看的，code 是给<b>客户端代码</b>看的，message 是给<b>人</b>看的。
 * 三者各司其职，不要混。
 */
public enum ApiErrors {

    // ---------------------------------------------------------------- 401 没有有效身份

    /** 接口需要登录，但请求没带令牌 / 令牌无效 / 令牌已过期。 */
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "请先登录"),

    /** 账号或密码错误。<b>刻意不区分「账号不存在」和「密码错」</b> —— 区分等于送出一个账号枚举接口。 */
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "手机号或密码错误"),

    /**
     * 请求没带有效的设备令牌，而当前是 enforce 档（见 {@code DeviceAuthProperties.Mode}）。
     *
     * <p>与 {@link #LOGIN_REQUIRED} 同为 401 但<b>不能合并</b>：这两件事的解法不一样。
     * 登录能解决前者，解决不了后者 —— 后者的真实原因几乎总是「客户端版本太旧」，
     * 而让一个装着旧版本的用户反复去登录，只会让他更确信是我们坏了。
     * 客户端要能按 code 分支，弹的是「去更新」而不是「去登录」。
     */
    DEVICE_REQUIRED(HttpStatus.UNAUTHORIZED, "请更新到最新版本后重试"),

    /**
     * 这台设备处在<b>观察档</b>，登录要多验一道验证码。
     *
     * <p>🔴 客户端见到它<b>不能当成登录失败</b>：密码是对的，只是还差一步。
     * 正确的反应是把验证码输入框亮出来，而不是把用户退回登录页从头再来。
     *
     * <p>措辞刻意不提「你的设备被标记了」—— 那句话对真实用户毫无意义
     * （他做不了任何事），只会让人以为账号出了问题去找客服。
     */
    DEVICE_VERIFICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "为了你的账号安全，请输入验证码后继续"),

    /**
     * 登录二次验证的凭票失效了：过期（5 分钟）、验证码错太多次被作废、或者换了设备。
     *
     * <p>客户端要<b>回到输密码那一步</b>重新登录（拿一张新票），而不是再点一次「获取验证码」——
     * 那张票已经不存在了，再点多少次都是这句话。所以它不能并进 BAD_CREDENTIALS。
     */
    CHALLENGE_EXPIRED(HttpStatus.UNAUTHORIZED, "验证已过期，请重新登录"),

    // ---------------------------------------------------------------- 403 身份有效但不允许

    /** 账号被冻结或已注销。 */
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "账号状态异常，请联系客服"),

    /** 这条数据不属于当前会员。<b>不返回 404</b> —— 用 404 区分「不存在」和「不是你的」同样是信息泄露。 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权访问"),

    /**
     * 在一台<b>不受信任的设备</b>上做敏感操作（新增收货地址、充话费），要先过一次邮箱验证码。
     * 见 {@code StepUpInterceptor}。
     *
     * <p>🔴 客户端见到它<b>不是失败</b>：身份有效、操作也合法，只是还差一步。
     * 正确的反应是调 {@code /auth/step-up/code} 发码、弹输入框、{@code /auth/step-up/verify}
     * 通过后<b>自动重试原请求</b> —— 而不是把用户退回上一页让他再点一次。
     *
     * <p>用 403 而不是 401：401 在客户端的语义是「身份没了」，会被当成要重新登录。
     * 这里身份完好，缺的是「此刻在这台设备上的可信度」。
     */
    STEP_UP_REQUIRED(HttpStatus.FORBIDDEN, "为了你的账号安全，请先完成邮箱验证"),

    // ---------------------------------------------------------------- 429 太频繁

    /** 触发了操作限制（连续登录失败等），要等到期或找客服解冻。 */
    OPERATION_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试"),

    // ---------------------------------------------------------------- 4xx 其它

    /** 入参不合法。校验框架的报错会被翻译成这个。 */
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "请求参数有误"),

    /** 资源不存在。 */
    NOT_FOUND(HttpStatus.NOT_FOUND, "请求的内容不存在"),

    /** 当前状态下这个操作做不了（活动已结束、订单已取消……）。 */
    CONFLICT(HttpStatus.CONFLICT, "当前状态下无法完成该操作"),

    // ---------------------------------------------------------------- 500

    /** 服务端自己的问题。<b>message 永远是这一句</b>，异常细节只进日志。 */
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "服务开小差了，请稍后再试");

    private final HttpStatus status;
    private final String defaultMessage;

    ApiErrors(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    /** 兜底文案。业务上有更具体的说法时，由 {@link ApiException} 覆盖。 */
    public String defaultMessage() {
        return defaultMessage;
    }

    /** 稳定的机器可读码，等于枚举名。客户端按它分支，<b>改名等于改接口契约</b>。 */
    public String code() {
        return name();
    }
}
