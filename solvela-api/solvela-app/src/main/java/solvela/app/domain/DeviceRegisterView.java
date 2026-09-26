package solvela.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 设备注册的返回。
 *
 * <h3>🔴 deviceToken 只在这一刻存在</h3>
 * 服务端<b>不存令牌</b>（它是自包含的，验签即可）。客户端必须把它<b>持久化</b> ——
 * 放内存里丢了就只能重新注册一台设备，那既会多出一行记录，也会消耗一次 IP 配额。
 *
 * <p>存的位置要选能跨重装的：iOS 用 Keychain，Android 存私有目录，
 * H5 只有 localStorage（清缓存就没了 —— 这是 H5 设备可信度天然低的根本原因，
 * 不是实现没做好）。
 *
 * @param deviceToken 设备令牌原文，之后每个请求都带在 {@code X-Device-Token} 头上
 * @param deviceId    设备号。不是秘密（令牌里就是明文），返回它是为了排查时能对上号
 */
@Schema(description = "设备注册结果")
public record DeviceRegisterView(

        @Schema(description = "设备令牌，客户端必须持久化。useCookie 时为 null —— 它已写进 HttpOnly cookie")
        String deviceToken,

        @Schema(description = "设备号，32 位 hex")
        String deviceId) {
}
