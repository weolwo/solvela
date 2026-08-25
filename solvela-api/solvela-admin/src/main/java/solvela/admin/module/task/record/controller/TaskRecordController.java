package solvela.admin.module.task.record.controller;

import solvela.base.common.domain.ValidateList;
import solvela.task.record.domain.entity.TaskRecord;
import solvela.task.record.domain.form.TaskRecordAddForm;
import solvela.task.record.domain.form.TaskRecordQueryForm;
import solvela.task.record.domain.form.TaskRecordStatusUpdateForm;
import solvela.task.record.domain.form.TaskRecordUpdateForm;
import solvela.task.record.domain.vo.TaskRecordFunnelVO;
import solvela.task.record.domain.vo.TaskRecordVO;
import solvela.task.record.service.TaskRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
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
    @SaCheckPermission("taskRecord:query")
    public ResponseDTO<PageResult<TaskRecordVO>> queryPage(@RequestBody @Valid TaskRecordQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "任务漏斗：达标率、任务分布、事件丢弃原因与数据一致性体检")
    @PostMapping("/funnel")
    @SaCheckPermission("taskRecord:query")
    public ResponseDTO<TaskRecordFunnelVO> funnel(@RequestBody @Valid TaskRecordQueryForm queryForm) {
        return ResponseDTO.ok(Service.funnel(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("taskRecord:add")
    public ResponseDTO<String> add(@RequestBody @Valid TaskRecordAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("taskRecord:update")
    public ResponseDTO<String> update(@RequestBody @Valid TaskRecordUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量禁用（置为 3-已过期，替代删除）")
    @PostMapping("/updateStatus")
    @SaCheckPermission("taskRecord:update")
    public ResponseDTO<String> updateStatus(@RequestBody @Valid TaskRecordStatusUpdateForm form) {
        return Service.updateStatus(form);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("taskRecord:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("taskRecord:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
