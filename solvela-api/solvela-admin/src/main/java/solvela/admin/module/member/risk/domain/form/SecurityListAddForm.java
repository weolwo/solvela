package solvela.admin.module.member.risk.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListTypeEnum;

import java.time.LocalDateTime;

/**
 * 新增一条名单。
 *
 * <h3>🔴 这里只做「必填 / 长度」这类纯输入校验</h3>
 * 「灰名单必须有分」「会员白名单必须填到期」「IP 格式对不对」都是<b>名单自己的语义</b>，
 * 放在域里（{@code MemberRiskAdminService.addList}）—— 否则下一个调用方绕过这张表单
 * 就能写出一条读出来会炸、或者永远不命中的名单。
 */
@Data
@Schema(description = "新增安全名单")
public class SecurityListAddForm {

    @Schema(description = "名单类型：1-黑, 2-灰, 3-白", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择名单类型")
    private SecurityListTypeEnum listType;

    @Schema(description = "维度：1-IP, 2-IP段, 3-身份摘要, 4-会员", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择维度")
    private SecurityListDimensionEnum dimension;

    @Schema(description = "IP / CIDR 原文，或会员号。身份维度请改填 identity")
    private String listValue;

    @Schema(description = "手机号或邮箱【明文】，服务端算摘要再存。身份维度用它")
    private String identity;

    @Schema(description = "灰名单命中加多少分。黑、白名单不要填")
    private Integer score;

    @Schema(description = "为什么进名单：给下一个看到它的人的人话", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请写明为什么进名单")
    @Size(max = 128, message = "原因最多 128 字")
    private String reason;

    @Schema(description = "到期时间。不填默认 30 天；会员白名单必填")
    private LocalDateTime expireTime;
}
