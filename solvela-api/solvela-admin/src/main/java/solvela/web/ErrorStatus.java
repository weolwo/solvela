package solvela.web;

import org.springframework.http.HttpStatus;
import solvela.code.ErrorCode;
import solvela.code.BizErrorCode;
import solvela.code.SystemErrorCode;
import solvela.code.UnexpectedErrorCode;
import solvela.code.UserErrorCode;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 错误码 → HTTP 状态码。
 *
 * <h3>为什么这张表在 solvela-web，而不是挂在枚举上</h3>
 * {@link ErrorCode} 住在 solvela-model，被七个业务模块共用，而那些模块
 * <b>不认识 {@code HttpStatus}</b> —— 这条线是上一轮刚立起来的（见 solvela-web 的 pom 注释）。
 * 把状态码写进枚举等于把 spring-web 拽回共享层，换来的只是少一张表。
 *
 * <p>另一个理由：同一个业务错误在不同端可以是不同状态。C 端有自己的 {@code ApiErrors}，
 * 它对「无权访问」的选择是 403 而不是 404 —— 那是<b>产品决定</b>，不是错误本身的属性。
 *
 * <h3>为什么穷举而不是按 level 兜底</h3>
 * 按类别兜底（user→400、system→500）看着省事，代价是新增一个错误码时<b>不会有人被问到</b>
 * 「它该是几」。于是「数据不存在」和「参数错误」一起变成 400，而前者本该是 404 ——
 * 这种错误不会有人报，因为界面上看起来一样。
 *
 * <p>穷举由 {@code ErrorStatusTest} 强制：新增一个错误码却没在这张表里写一行，<b>构建就红</b>。
 * {@link #of} 的 500 兜底是运行时的安全网（比如反序列化出一个本进程不认识的实现），
 * 不是开发期的默认值。
 */
public final class ErrorStatus {

    /**
     * IdentityHashMap：键是枚举常量，比较引用即可，比 hashCode 更快也更不会出错。
     */
    private static final Map<ErrorCode, HttpStatus> TABLE = new IdentityHashMap<>();

    static {
        // ---------------- 400 客户端把请求写错了 ----------------
        put(UserErrorCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);

        // ---------------- 401 没有有效身份 ----------------
        put(UserErrorCode.LOGIN_STATE_INVALID, HttpStatus.UNAUTHORIZED);
        // 「长时间未操作」也是 401：令牌已经不能用了。前端靠 code 区分要不要弹那个提示框
        put(UserErrorCode.LOGIN_ACTIVE_TIMEOUT, HttpStatus.UNAUTHORIZED);
        // 密码错了、还没被锁定 —— 认证失败，不是参数错误
        put(UserErrorCode.LOGIN_FAIL_WILL_LOCK, HttpStatus.UNAUTHORIZED);

        // ---------------- 403 身份有效但不允许 ----------------
        put(UserErrorCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        put(UserErrorCode.USER_STATUS_ERROR, HttpStatus.FORBIDDEN);
        put(UserErrorCode.ACCOUNT_FROZEN, HttpStatus.FORBIDDEN);

        // ---------------- 404 / 409 资源与状态 ----------------
        put(UserErrorCode.DATA_NOT_EXIST, HttpStatus.NOT_FOUND);
        put(UserErrorCode.ALREADY_EXIST, HttpStatus.CONFLICT);
        put(UserErrorCode.STATUS_ERROR, HttpStatus.CONFLICT);
        put(BizErrorCode.ACCOUNT_BALANCE_CHANGED, HttpStatus.CONFLICT);
        put(BizErrorCode.BALANCE_NOT_ENOUGH, HttpStatus.CONFLICT);
        put(BizErrorCode.AMOUNT_MUST_BE_GREATER_THAN_ZERO, HttpStatus.BAD_REQUEST);

        // ---------------- 429 太频繁 ----------------
        put(UserErrorCode.REPEAT_SUBMIT, HttpStatus.TOO_MANY_REQUESTS);
        put(UserErrorCode.FORM_REPEAT_SUBMIT, HttpStatus.TOO_MANY_REQUESTS);
        put(UserErrorCode.LOGIN_FAIL_LOCK, HttpStatus.TOO_MANY_REQUESTS);

        // ---------------- 5xx ----------------
        put(UserErrorCode.DEVELOPING, HttpStatus.NOT_IMPLEMENTED);
        put(SystemErrorCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        put(UnexpectedErrorCode.BUSINESS_HANDING, HttpStatus.SERVICE_UNAVAILABLE);
        put(UnexpectedErrorCode.PAY_ORDER_ID_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ErrorStatus() {
    }

    private static void put(ErrorCode code, HttpStatus status) {
        TABLE.put(code, status);
    }

    /**
     * 没登记的错误码一律 500 —— 让它进告警，而不是悄悄变成一个看着像正常业务的 400。
     */
    public static HttpStatus of(ErrorCode errorCode) {
        return TABLE.getOrDefault(errorCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
