package net.lab1024.sa.task.tasktemplate.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.task.tasktemplate.domain.entity.TaskTemplate;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateAddForm;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateQueryForm;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateSaveForm;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateUpdateForm;
import net.lab1024.sa.task.tasktemplate.domain.vo.TaskTemplateVO;
import net.lab1024.sa.task.tasktemplate.service.TaskTemplateService;
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
 * 任务模板表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "任务模板表")
@RequestMapping("/taskTemplate")
public class TaskTemplateController {

    private final TaskTemplateService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<TaskTemplateVO>> queryPage(@RequestBody @Valid TaskTemplateQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":addProposal")
    public ResponseDTO<String> add(@RequestBody @Valid TaskTemplateAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "模板设计器保存（按 templateCode upsert）")
    @PostMapping("/save")
    @SaCheckPermission(":save")
    public ResponseDTO<Boolean> save(@RequestBody @Valid TaskTemplateSaveForm saveForm) {
        return Service.save(saveForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid TaskTemplateUpdateForm updateForm) {
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
