package solvela.task.taskevent.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

/**
 * 任务事件分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskEventQuery extends PageParam {

    /** 事件编码（模糊） */
    private String eventCode;

    /** 展示名（模糊） */
    private String eventName;

    /** 状态：0-停用, 1-启用 */
    private Integer status;
}
