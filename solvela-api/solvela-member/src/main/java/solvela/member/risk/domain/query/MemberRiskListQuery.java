package solvela.member.risk.domain.query;

import lombok.Data;
import solvela.base.domain.PageParam;
import solvela.enums.MemberRiskListDimensionEnum;
import solvela.enums.MemberRiskListStatusEnum;
import solvela.enums.MemberRiskListTypeEnum;

/**
 * 名单查询条件。
 *
 * <h3>🔴 {@code listValue} 是精确匹配，不是模糊</h3>
 * 身份摘要那一维存的是 64 位 hex，模糊查没有意义；而 IP 维度上模糊查
 * （{@code LIKE '1.2.%'}）听起来有用，实际会让人以为自己查的是「这个网段」——
 * 而网段是另一个维度（CIDR），两者混起来会让人误判「这个 IP 到底被封了没有」。
 *
 * @Date 2026-10-16
 */
@Data
public class MemberRiskListQuery extends PageParam {

    private MemberRiskListTypeEnum listType;

    private MemberRiskListDimensionEnum dimension;

    /** IP / CIDR 原文、身份摘要（hex）、或会员号。精确匹配 */
    private String listValue;

    /** 不传 = 只看有效的。查历史（含已撤销）要显式传 */
    private MemberRiskListStatusEnum status;
}
