package solvela.admin.module.task.taskconfig.controller;

import solvela.base.common.domain.ValidateList;
import solvela.task.taskconfig.domain.entity.TaskConfig;
import solvela.task.taskconfig.domain.form.TaskConfigAddForm;
import solvela.task.taskconfig.domain.form.TaskConfigQueryForm;
import solvela.task.taskconfig.domain.form.TaskConfigUpdateForm;
import solvela.task.taskconfig.domain.form.TaskConfigStatusUpdateForm;
import solvela.task.taskconfig.domain.form.TaskConfigWizardSubmitForm;
import solvela.task.taskconfig.domain.form.TaskConfigWizardUpdateForm;
import solvela.task.taskconfig.domain.vo.TaskConfigWizardDetailVO;
import solvela.task.taskconfig.domain.vo.TaskConfigVO;
import solvela.task.taskconfig.service.TaskConfigService;
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
 * 任务配置表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 20:55:10
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "任务配置表")
@RequestMapping("/taskConfig")
public class TaskConfigController {

    private final TaskConfigService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("taskConfig:query")
    public ResponseDTO<PageResult<TaskConfigVO>> queryPage(@RequestBody @Valid TaskConfigQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("taskConfig:add")
    public ResponseDTO<String> add(@RequestBody @Valid TaskConfigAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "任务配置向导提交（主子表：taskConfig + prizeMappingList）")
    @PostMapping("/wizard/submit")
    @SaCheckPermission("taskConfig:wizard:submit")
    public ResponseDTO<Long> wizardSubmit(@RequestBody @Valid TaskConfigWizardSubmitForm submitForm) {
        return Service.wizardSubmit(submitForm);
    }

    @Operation(summary = "上/下线（列表页批量下线用它，替代删除）")
    @PostMapping("/updateStatus")
    @SaCheckPermission("taskConfig:update")
    public ResponseDTO<String> updateStatus(@RequestBody @Valid TaskConfigStatusUpdateForm form) {
        return Service.updateStatus(form);
    }

    @Operation(summary = "任务配置向导回显（主子表一次性返回，供编辑态铺回 5 个步骤）")
    @GetMapping("/wizard/detail/{id}")
    @SaCheckPermission("taskConfig:query")
    public ResponseDTO<TaskConfigWizardDetailVO> wizardDetail(@PathVariable Long id) {
        return Service.wizardDetail(id);
    }

    @Operation(summary = "任务配置向导更新（主表更新 + 奖励阶梯整体替换，同一事务）")
    @PostMapping("/wizard/update")
    @SaCheckPermission("taskConfig:wizard:submit")
    public ResponseDTO<Long> wizardUpdate(@RequestBody @Valid TaskConfigWizardUpdateForm updateForm) {
        return Service.wizardUpdate(updateForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("taskConfig:update")
    public ResponseDTO<String> update(@RequestBody @Valid TaskConfigUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("taskConfig:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("taskConfig:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
