package solvela.admin.module.task.tasktemplate.controller;

import solvela.base.domain.ValidateList;
import solvela.task.TaskTemplate;
import solvela.admin.module.task.tasktemplate.domain.form.TaskTemplateAddForm;
import solvela.task.tasktemplate.domain.command.TaskTemplateAddCommand;
import solvela.admin.module.task.tasktemplate.domain.form.TaskTemplateQueryForm;
import solvela.task.tasktemplate.domain.query.TaskTemplateQuery;
import solvela.admin.module.task.tasktemplate.domain.form.TaskTemplateSaveForm;
import solvela.task.tasktemplate.domain.command.TaskTemplateSaveCommand;
import solvela.admin.module.task.tasktemplate.domain.form.TaskTemplateStatusUpdateForm;
import solvela.admin.module.task.tasktemplate.domain.form.TaskTemplateUpdateForm;
import solvela.task.tasktemplate.domain.command.TaskTemplateUpdateCommand;
import solvela.task.tasktemplate.domain.dto.TaskTemplateOptionDTO;
import solvela.admin.module.task.tasktemplate.domain.vo.TaskTemplateVO;
import solvela.task.tasktemplate.domain.dto.TaskTemplateDTO;
import solvela.task.tasktemplate.service.TaskTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import solvela.web.ResponseDTO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import solvela.web.RequiresPermission;
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
    @RequiresPermission("taskTemplate:query")
    public ResponseDTO<PageResult<TaskTemplateVO>> queryPage(@RequestBody @Valid TaskTemplateQueryForm queryForm) {
        PageResult<TaskTemplateDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, TaskTemplateQuery.class));
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, TaskTemplateVO.class));
    }

    @Operation(summary = "生成模板编码（10位大写字母+数字，已判重）")
    @GetMapping("/generateCode")
    @RequiresPermission("taskTemplate:save")
    public ResponseDTO<String> generateTemplateCode() {
        return ResponseDTO.ok(Service.generateTemplateCode());
    }

    @Operation(summary = "任务向导用模板列表（ui_schema 以 JSON 对象下发）")
    @GetMapping("/optionList")
    @RequiresPermission("taskTemplate:query")
    public ResponseDTO<List<TaskTemplateOptionDTO>> queryOptionList() {
        return ResponseDTO.ok(Service.queryOptionList());
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @RequiresPermission("taskTemplate:add")
    public ResponseDTO<String> add(@RequestBody @Valid TaskTemplateAddForm addForm) {
        Service.add(SolvelaBeanUtil.copy(addForm, TaskTemplateAddCommand.class));
        return ResponseDTO.ok();
    }

    @Operation(summary = "模板设计器保存（按 templateCode upsert）")
    @PostMapping("/save")
    @RequiresPermission("taskTemplate:save")
    public ResponseDTO<Boolean> save(@RequestBody @Valid TaskTemplateSaveForm saveForm) {
        return ResponseDTO.ok(Service.save(SolvelaBeanUtil.copy(saveForm, TaskTemplateSaveCommand.class)));
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @RequiresPermission("taskTemplate:update")
    public ResponseDTO<String> update(@RequestBody @Valid TaskTemplateUpdateForm updateForm) {
        Service.update(SolvelaBeanUtil.copy(updateForm, TaskTemplateUpdateCommand.class));
        return ResponseDTO.ok();
    }

    @Operation(summary = "启用/禁用（单个开关与批量禁用共用）")
    @PostMapping("/updateStatus")
    @RequiresPermission("taskTemplate:update")
    public ResponseDTO<String> updateStatus(@RequestBody @Valid TaskTemplateStatusUpdateForm form) {
        Service.updateStatus(form.getIdList(), form.getStatus());
        return ResponseDTO.ok();
    }

    @Operation(summary = "模板详情（供模板设计器编辑态回显）")
    @GetMapping("/detail/{id}")
    @RequiresPermission("taskTemplate:query")
    public ResponseDTO<TaskTemplateVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(SolvelaBeanUtil.copy(Service.detail(id), TaskTemplateVO.class));
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @RequiresPermission("taskTemplate:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        Service.batchDelete(idList);
        return ResponseDTO.ok();
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @RequiresPermission("taskTemplate:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        Service.delete(id);
        return ResponseDTO.ok();
    }
}
