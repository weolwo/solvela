package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.enums.MemberRiskPhaseEnum;
import solvela.enums.MemberRiskRuleTypeEnum;

import java.time.LocalDateTime;

/**
 * 会员风控规则。<b>逻辑在代码里</b>（每条一个 {@code RiskRule} 实现），表里只存参数、分值与档位。
 *
 * @Date 2026-10-15
 */
@Data
@TableName("t_member_risk_rule")
public class MemberRiskRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 与代码里 {@code RiskRule#code()} 一一对应，启动时双向校验 */
    private String ruleCode;

    private String ruleName;

    private MemberRiskEventTypeEnum eventType;

    private MemberRiskPhaseEnum phase;

    private MemberRiskRuleTypeEnum ruleType;

    /** 固定处置。分级与打分规则为 null */
    private MemberRiskDecisionEnum directDecision;

    /** 打分规则命中时加的分 */
    private Integer score;

    /** 互斥组：同组命中只取最高分。null 表示独立证据 */
    private String ruleGroup;

    private MemberRiskModeEnum mode;

    /** 1 = 可信设备跳过本规则 */
    private Integer bypassByTrust;

    /** JSON 原文，形状由各规则声明 */
    private String params;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
