package solvela.admin.module.member.risk.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskModeEnum;

/**
 * 命中明细的一行。<b>客服回答「为什么让我验证」时念的就是 {@code detail}。</b>
 *
 * @Date 2026-10-16
 */
@Data
public class MemberRiskHitVO {

    private Long id;

    private String ruleCode;

    @Schema(description = "规则现在叫什么。规则被下线删掉之后为空，但这条命中照样看得到")
    private String ruleName;

    @Schema(description = "命中时规则所处的档位（规则档位会变，这里是当时的）：1-陪跑 2-生效")
    private MemberRiskModeEnum mode;

    @Schema(description = "这条规则给出的处置；打分规则为空")
    private MemberRiskDecisionEnum decision;

    private Integer score;

    @Schema(description = "命中依据，给人看的那句话")
    private String detail;

    @Schema(description = "1 = 特征取不到、按未命中处理。这一列的占比就是「这条规则是不是其实一直没在工作」")
    private Integer error;
}
