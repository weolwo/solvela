package solvela.admin.module.member.risk.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;

import java.time.LocalDateTime;

/**
 * 规则页的一行。
 *
 * <p>前四个字段<b>只读</b>（代码决定），后三个可改 —— 页面上要把这件事表达出来，
 * 否则运营会去试着改编码然后得到一个莫名其妙的错误。
 *
 * @Date 2026-10-16
 */
@Data
public class MemberRiskRuleVO {

    private Long id;

    @Schema(description = "规则编码。只读 —— 它与代码里的实现一一对应")
    private String ruleCode;

    private String ruleName;

    @Schema(description = "阶段：1-前置（验凭据之前）2-后置（验凭据之后）。只读")
    private MemberRiskPhaseEnum phase;

    @Schema(description = "类型：1-强规则（固定处置）2-强规则（分级/矩阵）3-打分规则。只读")
    private MemberRiskRuleTypeEnum ruleType;

    @Schema(description = "固定处置的强规则给出哪一档。只读")
    private MemberRiskDecisionEnum directDecision;

    @Schema(description = "互斥组：同组命中只取最高分。只读")
    private String ruleGroup;

    @Schema(description = "可信设备是否跳过：1-跳过 0-照常执行。只读")
    private Integer bypassByTrust;

    @Schema(description = "档位：0-关闭 1-陪跑 2-生效。可改")
    private MemberRiskModeEnum mode;

    @Schema(description = "命中加的分。可改")
    private Integer score;

    @Schema(description = "参数 JSON。可改 —— 保存时会用规则自己的校验过一遍")
    private String params;

    @Schema(description = "它在抓什么、为什么是这个阈值")
    private String description;

    private LocalDateTime updateTime;
}
