package solvela.admin.module.activity.controller;

import solvela.admin.module.activity.domain.form.ActivityConfigAddForm;
import solvela.activity.domain.command.ActivityConfigAddCommand;
import solvela.admin.module.activity.domain.form.ActivityConfigQueryForm;
import solvela.activity.domain.query.ActivityConfigQuery;
import solvela.admin.module.activity.domain.form.ActivityConfigUpdateForm;
import solvela.activity.domain.command.ActivityConfigUpdateCommand;
import solvela.admin.module.activity.domain.form.ActivityStatusUpdateForm;
import solvela.admin.module.activity.domain.form.ActivityTypeUpgradeForm;
import solvela.admin.module.activity.domain.form.ActivityWizardCreateForm;
import solvela.activity.domain.command.ActivityWizardCreateCommand;
import solvela.admin.module.activity.domain.vo.ActivityConfigVO;
import solvela.activity.domain.dto.ActivityConfigDTO;
import solvela.activity.domain.dto.ActivityDeleteCheckDTO;
import solvela.activity.service.ActivityConfigService;
import solvela.base.domain.ValidateList;
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
    @RequiresPermission("activityConfig:query")
    public PageResult<ActivityConfigVO> queryPage(@RequestBody @Valid ActivityConfigQueryForm queryForm) {
        PageResult<ActivityConfigDTO> page = activityConfigService.queryPage(SolvelaBeanUtil.copy(queryForm, ActivityConfigQuery.class));
        return SolvelaPageUtil.convert2PageResult(page, ActivityConfigVO.class);
    }

    @Operation(summary = "活动下拉列表（按类型过滤；includeInactive=true 时连已下线/已过期一并返回）")
    @GetMapping("/optionList")
    @RequiresPermission("activityConfig:query")
    public List<ActivityConfigVO> queryOptionList(
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false, defaultValue = "false") Boolean includeInactive) {
        return SolvelaBeanUtil.copyList(
                activityConfigService.queryOptionList(activityType, includeInactive), ActivityConfigVO.class);
    }

    @Operation(summary = "批量查询「是否已配置玩法」，供活动列表页一次算完")
    @PostMapping("/configuredStatus")
    @RequiresPermission("activityConfig:query")
    public Map<String, Boolean> queryConfiguredStatus(@RequestBody ValidateList<String> activityCodeList) {
        return activityConfigService.queryConfiguredStatus(activityCodeList);
    }

    @Operation(summary = "删除前检查：是否可删 + 下游引用明细")
    @GetMapping("/checkDeletable/{id}")
    @RequiresPermission("activityConfig:query")
    public ActivityDeleteCheckDTO checkDeletable(@PathVariable Long id) {
        return activityConfigService.checkDeletable(id);
    }

    @Operation(summary = "生成活动编码（10位大写字母+数字，已判重）")
    @GetMapping("/generateCode")
    @RequiresPermission("activityConfig:add")
    public String generateActivityCode() {
        return activityConfigService.generateActivityCode();
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @RequiresPermission("activityConfig:add")
    public void add(@RequestBody @Valid ActivityConfigAddForm addForm) {
        activityConfigService.add(SolvelaBeanUtil.copy(addForm, ActivityConfigAddCommand.class));
    }

    @Operation(summary = "创建向导第一步：建活动 + 随手建的若干奖品，一次事务落库")
    @PostMapping("/wizard/create")
    @RequiresPermission("activityConfig:add")
    public void wizardCreate(@RequestBody @Valid ActivityWizardCreateForm form) {
        ActivityWizardCreateCommand command = SolvelaBeanUtil.copy(form, ActivityWizardCreateCommand.class);
        // 🔴 嵌套集合必须显式转换。BeanUtils.copyProperties 会解析泛型，发现
        // List<WizardPrizeForm> 与 List<WizardPrizeCommand> 不兼容后直接<b>跳过</b>该属性 ——
        // 既不报错也不转换，prizeList 会留在 null，表现是"向导提交了但奖品一个没建"
        command.setPrizeList(SolvelaBeanUtil.copyList(
                form.getPrizeList(), ActivityWizardCreateCommand.WizardPrizeCommand.class));
        activityConfigService.wizardCreate(command);
    }

    @Operation(summary = "更新（不含活动类型，类型创建后不可改）")
    @PostMapping("/update")
    @RequiresPermission("activityConfig:update")
    public void update(@RequestBody @Valid ActivityConfigUpdateForm updateForm) {
        activityConfigService.update(SolvelaBeanUtil.copy(updateForm, ActivityConfigUpdateCommand.class));
    }

    @Operation(summary = "活动上下线（单个开关与批量操作共用）；上线前校验玩法完备度")
    @PostMapping("/updateStatus")
    @RequiresPermission("activityConfig:update")
    public void updateStatus(@RequestBody @Valid ActivityStatusUpdateForm form) {
        activityConfigService.updateStatus(form.getIdList(), form.getStatus());
    }

    @Operation(summary = "升级活动类型：仅 BASIC → DRAW/TASK/LOTTERY，且下游玩法表必须为空")
    @PostMapping("/upgradeType")
    @RequiresPermission("activityConfig:update")
    public void upgradeType(@RequestBody @Valid ActivityTypeUpgradeForm upgradeForm) {
        activityConfigService.upgradeType(upgradeForm.getId(), upgradeForm.getTargetType());
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @RequiresPermission("activityConfig:delete")
    public void batchDelete(@RequestBody ValidateList<Long> idList) {
        activityConfigService.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @RequiresPermission("activityConfig:delete")
    public void delete(@PathVariable Long id) {
        activityConfigService.delete(id);
    }
}
