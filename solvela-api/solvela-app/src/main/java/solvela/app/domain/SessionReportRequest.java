package solvela.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 「这不是我」：举报某一次登录。
 *
 * <h3>🔴 同样刻意没有 memberId</h3>
 * 会员号从令牌解析而来。收客户端传的话，任何人都能把别人的账号冻住。
 *
 * <h3>为什么要带 deviceId 和 loginTime</h3>
 * 它们<b>不是</b>用来认人的（认人只靠令牌），而是用来在决策流水里找到「那一次登录」
 * 并打上「确认被盗」。两者都来自上一次 {@code /auth/sessions} 的返回，客户端原样回传即可。
 *
 * <p>🔴 所以它们<b>可以为空、也可以是假的</b>，而那不会造成任何危害：
 * 填错只会导致一条分析数据标错或标不上（服务端还限定了 {@code member_id} 与一个 ±5 分钟的窗口），
 * 下线和冻结那两件真正有后果的事压根不看它们。
 */
@Schema(description = "举报某一次登录（这不是我）")
public record SessionReportRequest(

        @Schema(description = "会话ID，来自登录设备列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请指定要举报的会话")
        @Size(max = InputLimits.TOKEN_LENGTH, message = "会话不存在") String sessionId,

        @Schema(description = "那次登录的设备号，来自登录设备列表。老会话可能没有")
        @Size(max = InputLimits.TOKEN_LENGTH, message = "设备号不对")
        String deviceId,

        @Schema(description = "那次登录的时间（毫秒），来自登录设备列表。0 或不传表示未知")
        Long loginTime) {
}
