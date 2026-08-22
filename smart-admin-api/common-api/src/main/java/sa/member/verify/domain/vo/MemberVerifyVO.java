package sa.member.verify.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 会员实名信息（敏感，与主表分离） 列表VO
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */

@Data
public class MemberVerifyVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "会员号")
    private Long memberId;

    @Schema(description = "真实姓名密文")
    private String realName;

    @Schema(description = "身份证号密文")
    private String idCard;

    @Schema(description = "身份证HMAC-SHA256原始字节(32B)：查重与唯一约束走它")
    private String idCardHash;

    @Schema(description = "认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败")
    private Integer verifyStatus;

    @Schema(description = "认证通过时间")
    private LocalDateTime verifyTime;

    @Schema(description = "认证失败原因")
    private String failReason;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
