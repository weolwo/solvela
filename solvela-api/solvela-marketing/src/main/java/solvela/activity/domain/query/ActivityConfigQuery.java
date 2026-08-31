package solvela.activity.domain.query;

import solvela.enums.ActivityStatusEnum;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动配置分页查询的<b>领域参数</b>。形状与管理端的 {@code ActivityConfigQuery} 目前一致，
 * 但<b>变更的理由不同</b>：Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}（全项目第一个改造的样板）。
 * 这里刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class ActivityConfigQuery extends PageParam {

    /** 活动编码 */
    private String activityCode;

    /** 活动名称 */
    private String activityName;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

    /** 活动类型：BASIC / DRAW / TASK / LOTTERY */
    private String activityType;

    /** 状态：0-未开始, 1-上线, 2-下线 */
    private ActivityStatusEnum status;

}
