package solvela.task.taskconfig.domain.query;

import solvela.base.domain.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务配置分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class TaskConfigQuery extends PageParam {

    /** 任务名称 */
    private String taskName;

    /** 模板Code */
    private String templateCode;

    /** 活动编码 */
    private String activityCode;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 触发事件：取值来自注册表 t_task_event，不是固定枚举 */
    private String triggerEvent;

    /** 任务状态：1-待生效, 2-生效中, 3-已下线（见 TaskConst.CONFIG_STATUS_*） */
    private Integer status;

}
