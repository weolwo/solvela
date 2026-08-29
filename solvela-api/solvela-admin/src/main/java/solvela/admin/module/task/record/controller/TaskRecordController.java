package solvela.admin.module.task.record.controller;

import solvela.base.domain.ValidateList;
import solvela.task.TaskRecord;
import solvela.admin.module.task.record.domain.form.TaskRecordAddForm;
import solvela.task.record.domain.command.TaskRecordAddCommand;
import solvela.admin.module.task.record.domain.form.TaskRecordQueryForm;
import solvela.task.record.domain.query.TaskRecordQuery;
import solvela.admin.module.task.record.domain.form.TaskRecordStatusUpdateForm;
import solvela.admin.module.task.record.domain.form.TaskRecordUpdateForm;
import solvela.task.record.domain.command.TaskRecordUpdateCommand;
import solvela.task.record.domain.dto.TaskRecordFunnelDTO;
import solvela.admin.module.task.record.domain.vo.TaskRecordVO;
import solvela.task.record.domain.dto.TaskRecordDTO;
import solvela.task.record.service.TaskRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import solvela.web.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 任务记录表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "任务记录表")
@RequestMapping("/taskRecord")
public class TaskRecordController {

    private final TaskRecordService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @RequiresPermission("taskRecord:query")
    public PageResult<TaskRecordVO> queryPage(@RequestBody @Valid TaskRecordQueryForm queryForm) {
        PageResult<TaskRecordDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, TaskRecordQuery.class));
        return SolvelaPageUtil.convert2PageResult(page, TaskRecordVO.class);
    }

    @Operation(summary = "任务漏斗：达标率、任务分布、事件丢弃原因与数据一致性体检")
    @PostMapping("/funnel")
    @RequiresPermission("taskRecord:query")
    public TaskRecordFunnelDTO funnel(@RequestBody @Valid TaskRecordQueryForm queryForm) {
        return Service.funnel(SolvelaBeanUtil.copy(queryForm, TaskRecordQuery.class));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @RequiresPermission("taskRecord:add")
    public void add(@RequestBody @Valid TaskRecordAddForm addForm) {
        Service.add(SolvelaBeanUtil.copy(addForm, TaskRecordAddCommand.class));
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @RequiresPermission("taskRecord:update")
    public void update(@RequestBody @Valid TaskRecordUpdateForm updateForm) {
        Service.update(SolvelaBeanUtil.copy(updateForm, TaskRecordUpdateCommand.class));
    }

    @Operation(summary = "批量禁用（置为 3-已过期，替代删除）")
    @PostMapping("/updateStatus")
    @RequiresPermission("taskRecord:update")
    public void updateStatus(@RequestBody @Valid TaskRecordStatusUpdateForm form) {
        Service.updateStatus(form.getIdList(), form.getStatus());
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @RequiresPermission("taskRecord:delete")
    public void batchDelete(@RequestBody ValidateList<Long> idList) {
        Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @RequiresPermission("taskRecord:delete")
    public void batchDelete(@PathVariable Long id) {
        Service.delete(id);
    }
}
