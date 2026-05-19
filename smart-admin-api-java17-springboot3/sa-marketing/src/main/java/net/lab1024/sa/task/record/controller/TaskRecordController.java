package net.lab1024.sa.task.record.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.task.record.domain.entity.TaskRecord;
import net.lab1024.sa.task.record.domain.form.TaskRecordAddForm;
import net.lab1024.sa.task.record.domain.form.TaskRecordQueryForm;
import net.lab1024.sa.task.record.domain.form.TaskRecordUpdateForm;
import net.lab1024.sa.task.record.domain.vo.TaskRecordVO;
import net.lab1024.sa.task.record.service.TaskRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
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
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<TaskRecordVO>> queryPage(@RequestBody @Valid TaskRecordQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":addProposal")
    public ResponseDTO<String> add(@RequestBody @Valid TaskRecordAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid TaskRecordUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission(":delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission(":delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
