package solvela.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import solvela.code.BizErrorCode;
import solvela.code.ErrorCode;
import solvela.code.SystemErrorCode;
import solvela.code.UnexpectedErrorCode;
import solvela.code.UserErrorCode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每一个错误码都必须<b>被明确指定</b>一个 HTTP 状态。
 *
 * <p>这条测试挡的是「新加了错误码但没人被问到它该是几」：不写进表里，
 * {@link ErrorStatus#of} 会兜底成 500，于是一个「数据不存在」会以服务端故障的身份
 * 出现在告警曲线上。兜底是给运行时留的安全网，不是给开发期留的默认值。
 */
class ErrorStatusTest {

    /** 全部错误码。新增一个 ErrorCode 实现枚举时，也要加到这里 */
    private static final List<ErrorCode> ALL = new ArrayList<>() {{
        addAll(List.of(UserErrorCode.values()));
        addAll(List.of(SystemErrorCode.values()));
        addAll(List.of(UnexpectedErrorCode.values()));
        addAll(List.of(BizErrorCode.values()));
    }};

    @Test
    @DisplayName("🔴 每个错误码都在映射表里，没有一个是靠兜底的")
    void 映射表必须穷举() {
        List<String> missing = ALL.stream()
                // 兜底值就是 500，所以「映射结果是 500」和「没登记」在这里长得一样。
                // 用一个不可能被显式登记的探针值区分：真正想返回 500 的码在表里写着，
                // of() 会拿到表里的 INTERNAL_SERVER_ERROR，而不是这个探针
                .filter(code -> ErrorStatus.of(code) == HttpStatus.INTERNAL_SERVER_ERROR)
                .filter(code -> !EXPECTED_500.contains(code.name()))
                .map(ErrorCode::name)
                .toList();
        assertTrue(missing.isEmpty(),
                () -> "这些错误码没有登记 HTTP 状态，会兜底成 500：" + missing
                        + "\n请在 ErrorStatus 里为它们各写一行 —— 顺手想清楚它到底是 4xx 还是 5xx。");
    }

    /** 确实应该是 500 的那几个，单独列出来，免得和「忘了登记」混在一起 */
    private static final List<String> EXPECTED_500 = List.of(
            SystemErrorCode.SYSTEM_ERROR.name(),
            UnexpectedErrorCode.PAY_ORDER_ID_ERROR.name());

    @Test
    @DisplayName("鉴权类错误的状态码不能漂 —— 前端拦截器按它决定跳不跳登录页")
    void 鉴权错误状态码() {
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorStatus.of(UserErrorCode.LOGIN_STATE_INVALID));
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorStatus.of(UserErrorCode.LOGIN_ACTIVE_TIMEOUT));
        assertEquals(HttpStatus.FORBIDDEN, ErrorStatus.of(UserErrorCode.NO_PERMISSION));
    }

    @Test
    @DisplayName("响应体里的 code 就是枚举常量名 —— 改名等于改接口契约")
    void 机器码就是常量名() {
        assertEquals("PARAM_ERROR", UserErrorCode.PARAM_ERROR.name());
        assertEquals("BALANCE_NOT_ENOUGH", BizErrorCode.BALANCE_NOT_ENOUGH.name());
    }
}
