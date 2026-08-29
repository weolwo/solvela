package solvela.admin.module.prize.prizeconfig.controller;

import solvela.base.domain.ValidateList;
import solvela.prize.PrizeConfig;
import solvela.admin.module.prize.prizeconfig.domain.form.PrizeConfigAddForm;
import solvela.prize.prizeconfig.domain.command.PrizeConfigAddCommand;
import solvela.admin.module.prize.prizeconfig.domain.form.PrizeConfigQueryForm;
import solvela.prize.prizeconfig.domain.query.PrizeConfigQuery;
import solvela.admin.module.prize.prizeconfig.domain.form.PrizeConfigUpdateForm;
import solvela.prize.prizeconfig.domain.command.PrizeConfigUpdateCommand;
import solvela.admin.module.prize.prizeconfig.domain.form.PrizeStatusUpdateForm;
import solvela.admin.module.prize.prizeconfig.domain.vo.PrizeConfigVO;
import solvela.prize.prizeconfig.domain.dto.PrizeConfigDTO;
import solvela.prize.prizeconfig.service.PrizeConfigService;
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
 * 奖品配置表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "奖品配置表")
@RequestMapping("/prizeConfig")
public class PrizeConfigController {

    private final PrizeConfigService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @RequiresPermission("prizeConfig:query")
    public ResponseDTO<PageResult<PrizeConfigVO>> queryPage(@RequestBody @Valid PrizeConfigQueryForm queryForm) {
        PageResult<PrizeConfigDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, PrizeConfigQuery.class));
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, PrizeConfigVO.class));
    }

    @Operation(summary = "查询活动下启用中的奖品（抽奖工作台资产大库抽屉用）")
    @GetMapping("/optionList")
    @RequiresPermission("prizeConfig:query")
    public ResponseDTO<List<PrizeConfigVO>> queryEnabledList(@RequestParam String activityCode) {
        return ResponseDTO.ok(SolvelaBeanUtil.copyList(Service.queryEnabledList(activityCode), PrizeConfigVO.class));
    }

    @Operation(summary = "生成奖品编码（10位大写字母+数字，已判重）")
    @GetMapping("/generateCode")
    @RequiresPermission("prizeConfig:add")
    public ResponseDTO<String> generatePrizeCode() {
        return ResponseDTO.ok(Service.generatePrizeCode());
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @RequiresPermission("prizeConfig:add")
    public ResponseDTO<String> add(@RequestBody @Valid PrizeConfigAddForm addForm) {
        Service.add(SolvelaBeanUtil.copy(addForm, PrizeConfigAddCommand.class));
        return ResponseDTO.ok();
    }

    @Operation(summary = "奖品启用/禁用（单个开关与批量禁用共用）")
    @PostMapping("/updateStatus")
    @RequiresPermission("prizeConfig:update")
    public ResponseDTO<String> updateStatus(@RequestBody @Valid PrizeStatusUpdateForm form) {
        Service.updateStatus(form.getIdList(), form.getStatus());
        return ResponseDTO.ok();
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @RequiresPermission("prizeConfig:update")
    public ResponseDTO<String> update(@RequestBody @Valid PrizeConfigUpdateForm updateForm) {
        Service.update(SolvelaBeanUtil.copy(updateForm, PrizeConfigUpdateCommand.class));
        return ResponseDTO.ok();
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @RequiresPermission("prizeConfig:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        Service.batchDelete(idList);
        return ResponseDTO.ok();
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @RequiresPermission("prizeConfig:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        Service.delete(id);
        return ResponseDTO.ok();
    }
}
