package solvela.task.tasktemplate.domain.query;

import solvela.enums.EnableStatusEnum;
import solvela.base.domain.PageParam;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务模板分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class TaskTemplateQuery extends PageParam {

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 流转类型：SIMPLE(单次节点型), COUNT(计次型), AMOUNT(计额型) */
    private String taskType;

    /** 状态：0-禁用, 1-启用 */
    private EnableStatusEnum status;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

}
