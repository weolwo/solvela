package solvela.task.record.domain.command;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务记录表 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */

@Data
public class TaskRecordUpdateCommand {

    private Long id;

    /** 业务期数标识(防重用)：NONE, 日期(20260402) */
    private String periodKey;

    /** 开始时间 */
    private LocalDateTime validStartTime;

    /** 过期时间 */
    private LocalDateTime validEndTime;

    /** 状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期 */
    private Integer status;

    /** 进度详情 */
    private String progressData;

    /** 接取任务时的规则快照 */
    private String ruleSnapshot;

    /** 接取任务时的奖励快照 */
    private String prizeSnapshot;

    /** 达标时间 */
    private LocalDateTime completeTime;

}