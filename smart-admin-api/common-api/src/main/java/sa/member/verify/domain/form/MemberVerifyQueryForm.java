package sa.member.verify.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员实名信息（敏感，与主表分离） 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberVerifyQueryForm extends PageParam {

    @Schema(description = "会员号")
    private Long memberId;

    @Schema(description = "认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败")
    private Integer verifyStatus;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
