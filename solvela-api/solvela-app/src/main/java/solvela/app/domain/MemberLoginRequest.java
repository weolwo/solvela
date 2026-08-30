package solvela.app.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 手机号 + 密码登录的入参。
 *
 * <p>用 record：请求体读进来之后<b>不该被改</b>。上一版是 {@code @Data} 的可变类，
 * 于是「这个 phone 到了第三步还是不是用户传的那个」要读全链路才能回答。
 *
 * @param phone      手机号。<b>刻意不加 {@code @Pattern}</b> —— 格式校验在
 *                   {@code MemberPhoneUtil.normalize} 里，规范化和校验本来就是同一件事的两面。
 *                   在这里再写一条正则，就是第二份手机号规则，两份迟早对不上
 * @param password   密码明文，依赖 HTTPS 传输
 * @param deviceType 设备端：APP / H5 / WECHAT / PC，不传按 H5 记。
 *                   取值对齐 {@code t_member_login_log.device_type}
 */
public record MemberLoginRequest(
        @NotBlank(message = "请输入手机号") String phone,
        @NotBlank(message = "请输入密码") String password,
        String deviceType) {
}
