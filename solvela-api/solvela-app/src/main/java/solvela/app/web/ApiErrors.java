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

    // ---------------------------------------------------------------- 403 身份有效但不允许

    /** 账号被冻结或已注销。 */
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "账号状态异常，请联系客服"),

    /** 这条数据不属于当前会员。<b>不返回 404</b> —— 用 404 区分「不存在」和「不是你的」同样是信息泄露。 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权访问"),

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
