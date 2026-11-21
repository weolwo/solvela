package solvela.enums;

/**
 * 注册来源渠道：{@code t_member.register_source}，按枚举名存，JSON 也是枚举名。运营看渠道、风控识别批量注册都靠它。
 *
 * <p>客户端不能自报来源（MemberRegisterRequest 刻意没有这个字段）：网关按设备端推导，所以前四个值与 {@link DeviceTypeEnum} 同名；
 * 其余是服务端自己写的。认不出的落成 {@link #UNKNOWN} 并计一次 {@code CLIENT_VALUE_UNRECOGNIZED} 降级（MemberRegisterService）。
 */
public enum RegisterSourceEnum {

    H5,

    APP,

    WECHAT,

    PC,

    /** 邀请注册 */
    INVITE,

    /** 批量导入 */
    IMPORT,

    /** 蜜罐账号（CanaryAccountService 建的），风控规则 CanaryAccountRule 按它识别 */
    CANARY,

    /** 没传来源（库里的默认值也是它） */
    UNKNOWN,
}
