package solvela.prize.prizeconfig.domain.query;

import solvela.base.domain.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖品配置分页查询的<b>领域参数</b>。形状与管理端的 {@code PrizeConfigQuery} 目前一致，
 * 但<b>变更的理由不同</b>：Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}（全项目第一个改造的样板）。
 * 这里刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PrizeConfigQuery extends PageParam {

    /** 活动编码 */
    private String activityCode;

    /** 奖品编码 */
    private String prizeCode;

    /** 奖品级别 */
    private Integer prizeLevel;

    /** 奖品名称 */
    private String prizeName;

    /** 审批模式：0-自动免审, 1-人工审批 */
    private Integer approveMode;

    /** 状态：0-停用, 1-启用 */
    private Integer status;

}
