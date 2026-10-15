package solvela.member.risk.domain.dto;

import lombok.Data;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskModeEnum;

import java.time.LocalDateTime;

/**
 * 一条规则命中，给后台看。
 *
 * <p>它回答的是客服最常被问的那句话：「为什么让我验证？」—— {@code detail} 就是那个答案。
 *
 * @Date 2026-10-16
 */
@Data
public class MemberRiskHitDTO {

    private Long id;

    private String ruleCode;

    /** 规则此刻叫什么。从规则表现取 —— 命中明细只存编码，而编码不是给人看的 */
    private String ruleName;

    /** 命中时规则所处的档位（规则档位会变，这里是当时的） */
    private MemberRiskModeEnum mode;

    private MemberRiskDecisionEnum decision;

    private Integer score;

    private String detail;

    private Integer error;

    private LocalDateTime createTime;
}
