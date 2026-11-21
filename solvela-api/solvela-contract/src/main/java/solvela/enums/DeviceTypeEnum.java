package solvela.enums;

import java.util.Locale;

/**
 * 设备端：{@code t_device.device_type} 与 {@code t_member_login_log.device_type}（同名同口径），按枚举名存，JSON 也是枚举名。
 *
 * <p>设备端是客户端上报的。设备注册时认不出的直接拒（DeviceService），所以 t_device 里只有前四个值；
 * 登录日志则不能因为一个上报字段写错就把用户挡在门外 —— 认不出的落成 {@link #UNKNOWN}，并计一次
 * {@code CLIENT_VALUE_UNRECOGNIZED} 降级（MemberAuthService.saveLoginLog）。
 *
 * <p>⚠️ 只在落库这一刻解析。上游的请求、会话、设备令牌、风控上下文里它仍是客户端原样上报的字符串 ——
 * 设备令牌是签过名的格式、会话存在 Redis 里，改它们的表示形状换不来什么，风险却大得多。
 */
public enum DeviceTypeEnum {

    APP,

    H5,

    WECHAT,

    PC,

    /** 登录时上报了认不出的设备端 */
    UNKNOWN;

    /**
     * 客户端上报值 → 枚举：去空白、转大写后匹配；空串或认不出返回 null，由调用方决定是拒绝还是落成 UNKNOWN。
     */
    public static DeviceTypeEnum parseReported(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
