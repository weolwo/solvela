package solvela.admin.module.member.grade.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工调级表单。
 *
 * <h3>🔴 {@code reason} 是必填，且这一条不能松</h3>
 * 人工改数据而没有理由，半年后就是一条谁也解释不了的记录 ——
 * 而「为什么这个人是白金」正是审计第一个会问的问题。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
public class MemberGradeAdjustForm {

    @Schema(description = "会员号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择会员")
    private Long memberId;

    @Schema(description = "目标等级", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择目标等级")
    @Min(value = 0, message = "等级不能小于 0")
    private Integer newGrade;

    @Schema(description = "调整原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写调整原因")
    @Size(max = 255, message = "原因最多 255 个字")
    private String reason;
}
