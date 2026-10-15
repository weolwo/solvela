package solvela.member.risk.domain.dto;

import java.util.List;

/**
 * 一次尝试的全貌：结论 + 每条命中的规则。
 *
 * <p>「触发了什么规则、结果是什么」就是这两半合起来 —— 流水说<b>结论与结局</b>，
 * 命中明细说<b>每条规则各自给了什么、依据是什么</b>。后台详情页把两者并排展示。
 *
 * @Date 2026-10-16
 */
public record MemberRiskEventDetailDTO(MemberRiskEventDTO event, List<MemberRiskHitDTO> hits) {
}
