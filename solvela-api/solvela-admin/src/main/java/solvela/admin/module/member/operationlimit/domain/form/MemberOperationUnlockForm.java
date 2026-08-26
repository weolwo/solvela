package solvela.admin.module.member.operationlimit.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客服人工解冻会员操作限制。
 *
 * <p>只有会员号、操作类型、备注三个字段 —— 解冻方式固定为「人工」，操作人取当前登录员工，
 * 都不接受前端传值：这两列存在的唯一理由是事后追责，让调用方自己填等于没记。
 *
 * @Date 2026-08-26
 */
@Data
public class MemberOperationUnlockForm {

    @Schema(description = "会员号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号不能为空")
    private Long memberId;

    @Schema(description = "受限操作类型：1-登录, 2-修改密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "操作类型不能为空")
    private Integer operationType;

    /**
     * 刻意<b>必填</b>：人工解冻是绕过风控的动作，「为什么给他解」必须留下来。
     * 允许空备注的后果是这一列在几个月后全是 NULL，出问题时只能看到「某某解过」，
     * 而真正要回答的问题是「凭什么解」。
     */
    @Schema(description = "解冻原因，如「已电话核实身份」", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "解冻原因不能为空")
    @Size(max = 256, message = "解冻原因最长 256 字")
    private String remark;
}
