package solvela.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 客户端首次启动时领设备身份的入参。
 *
 * <h3>🔴 这里没有 deviceId</h3>
 * 设备号由服务端生成。客户端自报的话，脚本每次换一个 UUID，设备维度的计数永远是 1，
 * 限流形同虚设 —— 整套设备防刷的地基就是「服务端说了算」这一条。
 *
 * <p>下面几个字段仍然是客户端自报的，而且<b>没打算验</b>：只供人工排查
 * （「这批设备都自称是同一个型号」本身就是个信号），不参与任何判断。
 *
 * @param deviceType APP/H5/WECHAT/PC，不在集合里会被服务端拒绝
 * @param model      品牌型号，如 iPhone 15 Pro
 * @param osVersion  系统版本。区分 iOS/Android 靠它 —— deviceType 只到端
 * @param appVersion 应用版本
 */
@Schema(description = "设备注册入参")
public record DeviceRegisterRequest(

        @Schema(description = "设备端：APP/H5/WECHAT/PC", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "设备端不能为空")
        @Size(max = 16, message = "设备端最长 16 位")
        String deviceType,

        @Schema(description = "品牌型号，如 iPhone 15 Pro")
        @Size(max = 128, message = "型号过长")
        String model,

        @Schema(description = "系统版本，如 iOS 26.1")
        @Size(max = 64, message = "系统版本过长")
        String osVersion,

        @Schema(description = "应用版本，如 1.4.0")
        @Size(max = 64, message = "应用版本过长")
        String appVersion,
        @Schema(description = "Web 端传 true：凭证只经 HttpOnly cookie 下发，响应体里不带。"
                + "App / 自动化测试不传，凭证照旧在响应体里")
        Boolean useCookie) {

    public boolean cookieDelivery() {
        return Boolean.TRUE.equals(useCookie);
    }
}
