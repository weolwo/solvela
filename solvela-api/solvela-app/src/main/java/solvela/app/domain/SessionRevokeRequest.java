package solvela.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 让某一个登录会话下线。
 *
 * <h3>🔴 刻意没有 memberId</h3>
 * 会员号从令牌解析而来，<b>不收客户端传的</b>。收了就等于「说自己是谁就是谁」——
 * 任何人都能把别人的会话踢下线。判据与绑定邮箱那条一样。
 */
@Schema(description = "下线某个登录会话")
public record SessionRevokeRequest(

        @Schema(description = "会话ID，来自登录设备列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请指定要下线的会话")
        @Size(max = InputLimits.TOKEN_LENGTH, message = "会话不存在") String sessionId) {
}
