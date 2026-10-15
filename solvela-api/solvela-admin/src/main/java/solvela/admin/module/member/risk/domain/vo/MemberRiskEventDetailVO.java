package solvela.admin.module.member.risk.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 流水详情：结论 + 每条命中的规则。
 *
 * <p>「触发了什么规则、结果是什么」就是这两半合起来。
 *
 * @Date 2026-10-16
 */
@Data
public class MemberRiskEventDetailVO {

    private MemberRiskEventVO event;

    private List<MemberRiskHitVO> hits;
}
