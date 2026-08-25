package solvela.admin.module.task.tasktemplate.controller;

import solvela.base.common.domain.ValidateList;
import solvela.task.tasktemplate.domain.entity.TaskTemplate;
import solvela.task.tasktemplate.domain.form.TaskTemplateAddForm;
import solvela.task.tasktemplate.domain.form.TaskTemplateQueryForm;
import solvela.task.tasktemplate.domain.form.TaskTemplateSaveForm;
import solvela.task.tasktemplate.domain.form.TaskTemplateStatusUpdateForm;
import solvela.task.tasktemplate.domain.form.TaskTemplateUpdateForm;
import solvela.task.tasktemplate.domain.vo.TaskTemplateOptionVO;
import solvela.task.tasktemplate.domain.vo.TaskTemplateVO;
import solvela.task.tasktemplate.service.TaskTemplateService;
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

import java.util.List;

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
    @SaCheckPermission("taskTemplate:query")
    public ResponseDTO<PageResult<TaskTemplateVO>> queryPage(@RequestBody @Valid TaskTemplateQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "生成模板编码（10位大写字母+数字，已判重）")
    @GetMapping("/generateCode")
    @SaCheckPermission("taskTemplate:save")
    public ResponseDTO<String> generateTemplateCode() {
        return Service.generateTemplateCode();
    }

    @Operation(summary = "任务向导用模板列表（ui_schema 以 JSON 对象下发）")
    @GetMapping("/optionList")
    @SaCheckPermission("taskTemplate:query")
    public ResponseDTO<List<TaskTemplateOptionVO>> queryOptionList() {
        return Service.queryOptionList();
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("taskTemplate:add")
    public ResponseDTO<String> add(@RequestBody @Valid TaskTemplateAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "模板设计器保存（按 templateCode upsert）")
    @PostMapping("/save")
    @SaCheckPermission("taskTemplate:save")
    public ResponseDTO<Boolean> save(@RequestBody @Valid TaskTemplateSaveForm saveForm) {
        return Service.save(saveForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("taskTemplate:update")
    public ResponseDTO<String> update(@RequestBody @Valid TaskTemplateUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "启用/禁用（单个开关与批量禁用共用）")
    @PostMapping("/updateStatus")
    @SaCheckPermission("taskTemplate:update")
    public ResponseDTO<String> updateStatus(@RequestBody @Valid TaskTemplateStatusUpdateForm form) {
        return Service.updateStatus(form);
    }

    @Operation(summary = "模板详情（供模板设计器编辑态回显）")
    @GetMapping("/detail/{id}")
    @SaCheckPermission("taskTemplate:query")
    public ResponseDTO<TaskTemplateVO> detail(@PathVariable Long id) {
        return Service.detail(id);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("taskTemplate:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("taskTemplate:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
