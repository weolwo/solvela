package solvela.admin.module.member.grade.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 等级配置 新增/编辑表单。
 *
 * <p>⚠️ 这里只做「格式对不对」的校验。「门槛是否随等级递增」「0 档门槛是否为 0」
 * 这类<b>业务约束在 {@code MemberGradeConfigService}</b> —— 它们需要看到别的行，
 * 而表单校验只看得见自己这一行。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
public class MemberGradeConfigForm {

    @Schema(description = "id。新增时不传")
    private Long id;

    @Schema(description = "等级值：0 起，数字越大越高", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写等级")
    @Min(value = 0, message = "等级不能小于 0")
    private Integer gradeCode;

    @Schema(description = "等级名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写等级名")
    @Size(max = 32, message = "等级名最多 32 个字")
    private String gradeName;

    @Schema(description = "周期内成长值门槛（含）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写成长值门槛")
    @Min(value = 0, message = "门槛不能为负")
    private Long threshold;

    @Schema(description = "等级图标 file_id")
    private Long iconFileId;

    // 🔴 benefits 已废弃：权益搬到了 t_grade_privilege（一列 varchar 装不下图标、跳转、多语言）。
    //    这里刻意<b>不留</b>一个被忽略的字段 —— 留着的话前端照样能填，填完存不进去且不报错。
}
