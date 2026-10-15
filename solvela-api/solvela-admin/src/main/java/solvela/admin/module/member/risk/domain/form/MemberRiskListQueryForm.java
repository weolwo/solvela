package solvela.admin.module.member.risk.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;
import solvela.enums.MemberRiskListDimensionEnum;
import solvela.enums.MemberRiskListStatusEnum;
import solvela.enums.MemberRiskListTypeEnum;

/**
 * 名单查询。
 *
 * <h3>🔴 {@code identity} 收的是【明文】手机号 / 邮箱</h3>
 * 库里存的是摘要，而客服手上只有明文。由控制器规范化再算摘要（同决策流水页）——
 * 让客服自己算 hash 是不可能的事。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "风控名单查询")
public class MemberRiskListQueryForm extends PageParam {

    @Schema(description = "名单类型：1-黑, 2-灰, 3-白")
    private MemberRiskListTypeEnum listType;

    @Schema(description = "维度：1-IP, 2-IP段, 3-身份摘要, 4-会员")
    private MemberRiskListDimensionEnum dimension;

    @Schema(description = "IP / CIDR 原文，或会员号。精确匹配")
    private String listValue;

    @Schema(description = "手机号或邮箱【明文】，服务端算摘要再查。与 listValue 二选一")
    private String identity;

    @Schema(description = "状态：1-有效, 0-已撤销。不传只看有效的")
    private MemberRiskListStatusEnum status;
}
