package solvela.admin.module.member.grade.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

/**
 * 会员成长值 分页查询表单
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberGrowthQueryForm extends PageParam {

    @Schema(description = "会员号：精确匹配")
    private Long memberId;

    @Schema(description = "账号：模糊匹配")
    private String memberName;

    @Schema(description = "等级下限（含）")
    private Integer gradeMin;

    @Schema(description = "等级上限（含）")
    private Integer gradeMax;

    @Schema(description = "只看保级缓冲期内 / 只看不在缓冲期，不传为全部")
    private Boolean inProtect;
}
