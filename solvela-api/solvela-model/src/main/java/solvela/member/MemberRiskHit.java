package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskModeEnum;

import java.time.LocalDateTime;

/**
 * 风控规则命中明细（只记命中，按月分区）。
 *
 * @Date 2026-10-15
 */
@Data
@TableName("t_member_risk_hit")
public class MemberRiskHit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private String ruleCode;

    /** 命中时规则所处的档位 —— 规则档位会变，这里存当时的 */
    private MemberRiskModeEnum mode;

    /** 强规则 / 矩阵给出的处置；打分规则为 null */
    private MemberRiskDecisionEnum decision;

    private Integer score;

    /** 给人看的命中依据 */
    private String detail;

    /** 1 = 特征取不到、按未命中处理 */
    private Integer error;

    /** 🔴 必须等于所属决策流水的 create_time */
    private LocalDateTime createTime;
}
