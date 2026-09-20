package solvela.admin.module.member.grade.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import solvela.enums.EnableStatusEnum;

/**
 * 等级权益 新增/编辑表单。
 *
 * <p>⚠️ 这里只做「格式对不对」的校验。「这一档存不存在」「同档编码是否重复」
 * 这类<b>业务约束在 {@code GradePrivilegeService}</b> —— 它们要看别的行，
 * 而表单校验只看得见自己这一行。
 *
 * @author alaric
 * @date 2026-09-21
 */
@Data
public class GradePrivilegeForm {

    @Schema(description = "id。新增时不传")
    private Long id;

    @Schema(description = "等级值，关联等级配置的 gradeCode", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择等级")
    @Min(value = 0, message = "等级不能小于 0")
    private Integer gradeCode;

    /**
     * 🔴 限定大写字母 + 下划线，是为了让它保持「编码」而不是滑成一句中文说明。
     *
     * <p>它没有任何引擎去读，唯一的作用是让「白金和钻石都有生日礼」这件事
     * 在数据里<b>看得出来</b> —— 一旦允许写自由文本，同一个权益在两档里
     * 会被写成两种说法，那条信息就没了。
     */
    @Schema(description = "权益编码，如 BIRTHDAY_GIFT。大写字母与下划线",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写权益编码")
    @Size(max = 64, message = "权益编码最多 64 个字符")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "权益编码只能是大写字母、数字和下划线，且以字母开头")
    private String privilegeCode;

    @Schema(description = "权益名，直接展示给用户", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写权益名")
    @Size(max = 64, message = "权益名最多 64 个字")
    private String privilegeName;

    @Schema(description = "权益说明，等级页的第二行小字")
    @Size(max = 255, message = "权益说明最多 255 个字")
    private String description;

    @Schema(description = "图标 file_id")
    private Long iconFileId;

    @Schema(description = "点进去跳哪儿。为空表示纯展示、不可点")
    @Size(max = 255, message = "跳转地址最多 255 个字符")
    private String actionUrl;

    @Schema(description = "展示顺序，越大越靠前。不传按 0")
    private Integer sort;

    @Schema(description = "状态。不传按启用")
    private EnableStatusEnum status;
}
