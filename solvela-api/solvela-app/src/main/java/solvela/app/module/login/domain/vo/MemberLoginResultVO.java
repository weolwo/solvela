package solvela.app.module.login.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录结果 / 当前登录态。
 *
 * <p>登录接口和 {@code getLoginInfo} 返回同一个结构，前端刷新页面时可以直接用
 * {@code getLoginInfo} 的结果覆盖本地状态，不必维护两套字段映射。
 *
 * @Date 2026-08-25
 */
@Data
@Schema(description = "会员登录结果")
public class MemberLoginResultVO {

    @Schema(description = "会员号")
    private Long memberId;

    @Schema(description = "账号")
    private String memberName;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像 file_id")
    private Long avatarFileId;

    @Schema(description = "性别：0-未知, 1-男, 2-女")
    private Integer gender;

    /**
     * ⚠️ {@code getLoginInfo} 也会带上 token（回填当前请求用的那个），
     * 前端不用区分两个接口的返回。
     */
    @Schema(description = "登录令牌，后续请求放在 Authorization 头里，格式 Bearer {token}")
    private String token;
}
