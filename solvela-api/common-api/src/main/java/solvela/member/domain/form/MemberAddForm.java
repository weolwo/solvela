package solvela.member.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员主表 新建表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:39:08
 * @Copyright weolwo
 */

@Data
public class MemberAddForm {

    @Schema(description = "会员号：10位数字(1000000000~9999999999)。全链路关联键+迁移锚点，永不可变", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号：10位数字(1000000000~9999999999)。全链路关联键+迁移锚点，永不可变 不能为空")
    private Long memberId;

    @Schema(description = "账号：微信号风格，字母开头6~20位[A-Za-z][A-Za-z0-9_-]。全局唯一(大小写不敏感)，用户可改", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "账号：微信号风格，字母开头6~20位[A-Za-z][A-Za-z0-9_-]。全局唯一(大小写不敏感)，用户可改 不能为空")
    private String memberName;

    @Schema(description = "昵称：中文随意，用户可改。?任何地方都不许拿它做关联键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "昵称：中文随意，用户可改。?任何地方都不许拿它做关联键 不能为空")
    private String nickname;

    @Schema(description = "性别：0-未知, 1-男, 2-女", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "性别：0-未知, 1-男, 2-女 不能为空")
    private Integer gender;

    @Schema(description = "状态：1-正常, 2-冻结(风控/违规), 3-已注销", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态：1-正常, 2-冻结(风控/违规), 3-已注销 不能为空")
    private Integer status;

    @Schema(description = "注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT... 不能为空")
    private String registerSource;

}