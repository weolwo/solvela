package solvela.admin.module.member.verify.domain.form;

import solvela.enums.MemberVerifyStatusEnum;
import solvela.base.domain.PageParam;
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

    @Schema(description = "会员号：精确匹配")
    private Long memberId;

    /**
     * 账号，模糊匹配。
     *
     * <p>这个页面<b>没有按姓名/身份证搜索</b>：那两列在库里是密文，SQL 层没法模糊匹配，
     * 真要按姓名找人只能全表解密再比对。所以检索入口只有账号 / 会员号 / 状态 / 时间。
     */
    @Schema(description = "账号：模糊匹配")
    private String memberName;

    @Schema(description = "认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败")
    private MemberVerifyStatusEnum verifyStatus;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
