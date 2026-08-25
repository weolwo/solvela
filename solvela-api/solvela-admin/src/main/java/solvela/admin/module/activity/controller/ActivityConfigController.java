package solvela.admin.module.activity.controller;

import solvela.activity.domain.form.ActivityConfigAddForm;
import solvela.activity.domain.form.ActivityConfigQueryForm;
import solvela.activity.domain.form.ActivityConfigUpdateForm;
import solvela.activity.domain.form.ActivityStatusUpdateForm;
import solvela.activity.domain.form.ActivityTypeUpgradeForm;
import solvela.activity.domain.form.ActivityWizardCreateForm;
import solvela.activity.domain.vo.ActivityConfigVO;
import solvela.activity.domain.vo.ActivityDeleteCheckVO;
import solvela.activity.service.ActivityConfigService;
import solvela.base.common.domain.ValidateList;
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
import java.util.Map;

/**
 * 活动配置 Controller
 *
 * 权限串统一为 activityConfig:xxx，与 t_menu 的功能点一一对应。
 * 此前 add/generateCode 上挂的是 ":addProposal"（提案域的动作名被整段复制过来），
 * 且冒号前为空 —— 实际权限串是 ":addProposal"，与任何功能点都对不上。
 * 这类问题在开发期测不出来：管理员账号往往有全量权限，只有给运营配了受限角色才暴露。
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "活动配置")
@RequestMapping("/activityConfig")
public class ActivityConfigController {

    private final ActivityConfigService activityConfigService;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("activityConfig:query")
    public ResponseDTO<PageResult<ActivityConfigVO>> queryPage(@RequestBody @Valid ActivityConfigQueryForm queryForm) {
        return ResponseDTO.ok(activityConfigService.queryPage(queryForm));
    }

    @Operation(summary = "活动下拉列表（按类型过滤；includeInactive=true 时连已下线/已过期一并返回）")
    @GetMapping("/optionList")
    @SaCheckPermission("activityConfig:query")
    public ResponseDTO<List<ActivityConfigVO>> queryOptionList(
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false, defaultValue = "false") Boolean includeInactive) {
        return ResponseDTO.ok(activityConfigService.queryOptionList(activityType, includeInactive));
    }

    @Operation(summary = "批量查询「是否已配置玩法」，供活动列表页一次算完")
    @PostMapping("/configuredStatus")
    @SaCheckPermission("activityConfig:query")
    public ResponseDTO<Map<String, Boolean>> queryConfiguredStatus(@RequestBody ValidateList<String> activityCodeList) {
        return ResponseDTO.ok(activityConfigService.queryConfiguredStatus(activityCodeList));
    }

    @Operation(summary = "删除前检查：是否可删 + 下游引用明细")
    @GetMapping("/checkDeletable/{id}")
    @SaCheckPermission("activityConfig:query")
    public ResponseDTO<ActivityDeleteCheckVO> checkDeletable(@PathVariable Long id) {
        return ResponseDTO.ok(activityConfigService.checkDeletable(id));
    }

    @Operation(summary = "生成活动编码（10位大写字母+数字，已判重）")
    @GetMapping("/generateCode")
    @SaCheckPermission("activityConfig:add")
    public ResponseDTO<String> generateActivityCode() {
        return activityConfigService.generateActivityCode();
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("activityConfig:add")
    public ResponseDTO<String> add(@RequestBody @Valid ActivityConfigAddForm addForm) {
        return activityConfigService.add(addForm);
    }

    @Operation(summary = "创建向导第一步：建活动 + 随手建的若干奖品，一次事务落库")
    @PostMapping("/wizard/create")
    @SaCheckPermission("activityConfig:add")
    public ResponseDTO<String> wizardCreate(@RequestBody @Valid ActivityWizardCreateForm form) {
        return activityConfigService.wizardCreate(form);
    }

    @Operation(summary = "更新（不含活动类型，类型创建后不可改）")
    @PostMapping("/update")
    @SaCheckPermission("activityConfig:update")
    public ResponseDTO<String> update(@RequestBody @Valid ActivityConfigUpdateForm updateForm) {
        return activityConfigService.update(updateForm);
    }

    @Operation(summary = "活动上下线（单个开关与批量操作共用）；上线前校验玩法完备度")
    @PostMapping("/updateStatus")
    @SaCheckPermission("activityConfig:update")
    public ResponseDTO<String> updateStatus(@RequestBody @Valid ActivityStatusUpdateForm form) {
        return activityConfigService.updateStatus(form);
    }

    @Operation(summary = "升级活动类型：仅 BASIC → DRAW/TASK/LOTTERY，且下游玩法表必须为空")
    @PostMapping("/upgradeType")
    @SaCheckPermission("activityConfig:update")
    public ResponseDTO<String> upgradeType(@RequestBody @Valid ActivityTypeUpgradeForm upgradeForm) {
        return activityConfigService.upgradeType(upgradeForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("activityConfig:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return activityConfigService.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("activityConfig:delete")
    public ResponseDTO<String> delete(@PathVariable Long id) {
        return activityConfigService.delete(id);
    }
}
