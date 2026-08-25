package solvela.member.verify.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import solvela.member.constant.MemberConst;

/**
 * 实名审核驳回。
 *
 * @Date 2026-08-23
 */
@Data
public class MemberVerifyRejectForm {

    @Schema(description = "实名记录id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "记录id不能为空")
    private Long id;

    /**
     * <b>必填</b>：用户在 C 端看到的就是这句话。不填的话他只知道失败了、不知道该改什么，
     * 只能反复重交，而每次重交都要运营再审一遍。
     */
    @Schema(description = "驳回原因，用户可见", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写驳回原因，用户会看到这句话")
    @Size(max = MemberConst.MAX_FAIL_REASON_LENGTH, message = "驳回原因最长 255 字")
    private String failReason;
}
