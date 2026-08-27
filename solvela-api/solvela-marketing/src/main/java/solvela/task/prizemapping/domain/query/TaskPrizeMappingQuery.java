package solvela.task.prizemapping.domain.query;

import solvela.base.domain.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务奖品映射分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class TaskPrizeMappingQuery extends PageParam {

    /** 活动编码 */
    private String activityCode;

    /** 任务名称（模糊） */
    private String taskName;

    /** 任务状态：1-待生效, 2-生效中, 3-已下线 */
    private Integer taskStatus;

    /** 任务配置ID */
    private Long taskConfigId;

    /** 奖励编码 */
    private String prizeCode;

}
