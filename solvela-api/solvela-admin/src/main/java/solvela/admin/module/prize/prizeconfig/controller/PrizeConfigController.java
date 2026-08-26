package solvela.admin.module.prize.prizeconfig.controller;

import solvela.base.domain.ValidateList;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.domain.form.PrizeConfigAddForm;
import solvela.admin.module.prize.prizeconfig.domain.form.PrizeConfigQueryForm;
import solvela.prize.prizeconfig.domain.query.PrizeConfigQuery;
import solvela.prize.prizeconfig.domain.form.PrizeConfigUpdateForm;
import solvela.prize.prizeconfig.domain.form.PrizeStatusUpdateForm;
import solvela.admin.module.prize.prizeconfig.domain.vo.PrizeConfigVO;
import solvela.prize.prizeconfig.domain.dto.PrizeConfigDTO;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import solvela.base.domain.ResponseDTO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
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
    @SaCheckPermission("prizeConfig:query")
    public ResponseDTO<PageResult<PrizeConfigVO>> queryPage(@RequestBody @Valid PrizeConfigQueryForm queryForm) {
        PageResult<PrizeConfigDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, PrizeConfigQuery.class));
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, PrizeConfigVO.class));
    }

    @Operation(summary = "查询活动下启用中的奖品（抽奖工作台资产大库抽屉用）")
    @GetMapping("/optionList")
    @SaCheckPermission("prizeConfig:query")
    public ResponseDTO<List<PrizeConfigVO>> queryEnabledList(@RequestParam String activityCode) {
        return ResponseDTO.ok(SolvelaBeanUtil.copyList(Service.queryEnabledList(activityCode), PrizeConfigVO.class));
    }

    @Operation(summary = "生成奖品编码（10位大写字母+数字，已判重）")
    @GetMapping("/generateCode")
    @SaCheckPermission("prizeConfig:add")
    public ResponseDTO<String> generatePrizeCode() {
        return Service.generatePrizeCode();
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("prizeConfig:add")
    public ResponseDTO<String> add(@RequestBody @Valid PrizeConfigAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "奖品启用/禁用（单个开关与批量禁用共用）")
    @PostMapping("/updateStatus")
    @SaCheckPermission("prizeConfig:update")
    public ResponseDTO<String> updateStatus(@RequestBody @Valid PrizeStatusUpdateForm form) {
        return Service.updateStatus(form);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("prizeConfig:update")
    public ResponseDTO<String> update(@RequestBody @Valid PrizeConfigUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("prizeConfig:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("prizeConfig:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
