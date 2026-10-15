package solvela.member.risk.domain;

import lombok.Data;
import solvela.enums.MemberRiskListDimensionEnum;
import solvela.enums.MemberRiskListTypeEnum;

import java.time.LocalDateTime;

/**
 * 新增一条名单。
 *
 * <h3>🔴 {@code listValue} 由【接入层】规范化好再传进来</h3>
 * 身份维度要的是 {@code PiiHasher} 的摘要，而后台页面上输的是<b>明文</b>手机号 / 邮箱 ——
 * 算摘要要先规范化（大小写、空格、+86），而那套工具在会员域里。
 * 所以接入层负责「明文 → 摘要」，这里只接受已经是摘要的值。
 * 让这一层去收明文的话，「页面上输什么」就成了这个命令的隐含契约，
 * 而下一个调用方（比如客服工具）不会知道。
 *
 * @param expireTime 到期时间。null = 长期；<b>会员白名单必填</b>（见 {@code MemberRiskAdminService}）
 * @param score      灰名单命中加多少分。黑、白名单必须为 null
 */
@Data
public class MemberRiskListAddCmd {

    private MemberRiskListTypeEnum listType;

    private MemberRiskListDimensionEnum dimension;

    private String listValue;

    private Integer score;

    private String reason;

    private LocalDateTime expireTime;

    private String operator;
}
