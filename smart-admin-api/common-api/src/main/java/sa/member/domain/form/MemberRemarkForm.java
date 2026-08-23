package sa.member.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import sa.member.constant.MemberConst;

/**
 * 保存运营备注。
 *
 * <p>只有会员号和备注两个字段 —— 备注是运营唯一有权改的会员字段，
 * 开一个能改整行的表单等于把昵称、状态、注册来源也一并暴露给了这个入口。
 *
 * @Date 2026-08-23
 */
@Data
public class MemberRemarkForm {

    @Schema(description = "会员号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号不能为空")
    private Long memberId;

    /** 允许为空：清空备注是合法动作（实体上那一列挂了 updateStrategy = ALWAYS 才写得回 null） */
    @Schema(description = "运营备注，传空即清空")
    @Size(max = MemberConst.MAX_REMARK_LENGTH, message = "备注最长 255 字")
    private String remark;
}
