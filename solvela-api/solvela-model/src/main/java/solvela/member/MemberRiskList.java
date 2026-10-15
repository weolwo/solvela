package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.MemberRiskListDimensionEnum;
import solvela.enums.MemberRiskListStatusEnum;
import solvela.enums.MemberRiskListTypeEnum;

import java.time.LocalDateTime;

/**
 * 会员风控名单。撤销不删行（{@code status = 0} + 撤销人、原因、时间），谁加的、谁解的都查得到。
 *
 * @Date 2026-10-15
 */
@Data
@TableName("t_member_risk_list")
public class MemberRiskList {

    @TableId(type = IdType.AUTO)
    private Long id;

    private MemberRiskListTypeEnum listType;

    private MemberRiskListDimensionEnum dimension;

    /** IP / CIDR 原文；身份摘要存十六进制；会员存会员号 */
    private String listValue;

    /** 灰名单命中加的分 */
    private Integer score;

    private String reason;

    /** 1-人工, 2-自动（预留） */
    private Integer source;

    private MemberRiskListStatusEnum status;

    /** null = 长期 */
    private LocalDateTime expireTime;

    private String operator;

    private String revokeOperator;

    private String revokeReason;

    private LocalDateTime revokeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
