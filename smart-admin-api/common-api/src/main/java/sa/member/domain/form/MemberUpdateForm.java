package sa.member.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员主表 更新表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:39:08
 * @Copyright weolwo
 */

@Data
public class MemberUpdateForm {

    @Schema(description = "会员号：10位数字(1000000000~9999999999)。全链路关联键+迁移锚点，永不可变", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号：10位数字(1000000000~9999999999)。全链路关联键+迁移锚点，永不可变 不能为空")
    private Long memberId;

}