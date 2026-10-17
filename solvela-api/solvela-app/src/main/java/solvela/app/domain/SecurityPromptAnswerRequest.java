package solvela.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 回答一条「你的账号刚在 XX 登录，是你吗？」。
 *
 * <h3>🔴 {@code isMe} 是 {@code Boolean} 而不是 {@code boolean}</h3>
 * 基本类型在请求体里漏了这个字段时，Jackson 会抛
 * {@code Cannot map null into type boolean}，客户端拿到的是一个看不懂的 400。
 * 用包装类型 + {@code @NotNull}，漏了会得到「请明确回答是或不是」——
 * 而且<b>绝不默认成 true</b>：默认「是我」等于帮盗号者自动确认。
 * （同一个坑在 {@code MemberAuthCmd.captchaPassed} 上踩过一次。）
 */
@Schema(description = "回答「刚才是你吗」")
public record SecurityPromptAnswerRequest(

        @Schema(description = "确认单号，来自待确认列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请指定要回答的确认")
        @Size(max = InputLimits.TOKEN_LENGTH, message = "确认已失效") String promptId,

        @Schema(description = "是不是你本人：true-是我，false-不是我",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请明确回答是或不是") Boolean isMe) {
}
