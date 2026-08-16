package sa.task.record.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务记录表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class TaskRecordQueryForm extends PageParam {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "开始时间")
    private LocalDate validStartTimeBegin;

    @Schema(description = "开始时间")
    private LocalDate validStartTimeEnd;

    @Schema(description = "状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期")
    private Integer status;

    @Schema(description = "达标时间")
    private LocalDate completeTimeBegin;

    @Schema(description = "达标时间")
    private LocalDate completeTimeEnd;

}
