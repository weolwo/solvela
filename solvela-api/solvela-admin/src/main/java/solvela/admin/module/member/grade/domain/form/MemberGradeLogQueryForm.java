package solvela.admin.module.member.grade.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

/**
 * 等级变更留痕 分页查询表单。
 *
 * <p>会员可以不填 ——「最近一段时间谁被人工调级了」是真实的审计场景。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberGradeLogQueryForm extends PageParam {

    @Schema(description = "会员号：精确匹配")
    private Long memberId;

    @Schema(description = "账号：模糊匹配")
    private String memberName;

    @Schema(description = "变更类型：UPGRADE/DOWNGRADE/KEEP/MANUAL/RISK_REVOKE")
    private String changeType;

    @Schema(description = "操作人")
    private String operator;

    @Schema(description = "发生时间-开始")
    private LocalDate createTimeBegin;

    @Schema(description = "发生时间-结束")
    private LocalDate createTimeEnd;
}
