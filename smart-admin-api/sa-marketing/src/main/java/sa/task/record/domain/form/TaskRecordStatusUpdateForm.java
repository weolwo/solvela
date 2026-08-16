package sa.task.record.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 任务记录 状态变更表单（列表页的批量禁用用它）。
 *
 * <p>⚠️ t_task_record.status 里<b>没有「禁用」这一档</b>（只有 0-进行中/1-已完成/2-已发奖/3-已过期）。
 * 管理端所谓的「禁用一条任务记录」，实际语义是「让它不再推进、不再发奖」，
 * 对应的终态只有 3-已过期 —— 这也正是过期任务的收口方式
 * （见索引 idx_t_tsk_rec_expire (status, valid_end_time)）。
 * 所以这里只接受 3，不额外造一个库里不存在的状态值。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
public class TaskRecordStatusUpdateForm {

    @Schema(description = "任务记录id列表，单个操作也用列表传", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少选择一条任务记录")
    private List<Long> idList;

    @Schema(description = "目标状态：当前仅支持 3-已过期（管理端的「禁用」）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标状态 不能为空")
    private Integer status;
}
