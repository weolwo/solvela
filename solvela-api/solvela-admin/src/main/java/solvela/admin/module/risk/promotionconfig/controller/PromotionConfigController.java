package solvela.admin.module.risk.promotionconfig.controller;

import solvela.base.domain.ValidateList;
import solvela.risk.PromotionConfig;
import solvela.admin.module.risk.promotionconfig.domain.form.PromotionConfigAddForm;
import solvela.risk.promotionconfig.domain.command.PromotionConfigAddCommand;
import solvela.admin.module.risk.promotionconfig.domain.form.PromotionConfigQueryForm;
import solvela.risk.promotionconfig.domain.query.PromotionConfigQuery;
import solvela.admin.module.risk.promotionconfig.domain.form.PromotionConfigUpdateForm;
import solvela.risk.promotionconfig.domain.command.PromotionConfigUpdateCommand;
import solvela.risk.promotionconfig.domain.dto.PromotionConfigOptionDTO;
import solvela.admin.module.risk.promotionconfig.domain.vo.PromotionConfigVO;
import solvela.risk.promotionconfig.domain.dto.PromotionConfigDTO;
import solvela.risk.promotionconfig.service.PromotionConfigService;
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
 * 优惠配置表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "优惠配置表")
@RequestMapping("/promotionConfig")
public class PromotionConfigController {

    private final PromotionConfigService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @RequiresPermission("promotionConfig:query")
    public ResponseDTO<PageResult<PromotionConfigVO>> queryPage(@RequestBody @Valid PromotionConfigQueryForm queryForm) {
        PageResult<PromotionConfigDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, PromotionConfigQuery.class));
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, PromotionConfigVO.class));
    }

    @Operation(summary = "优惠配置下拉列表（全量启用中，前端按 prizeType 分组做级联）")
    @GetMapping("/optionList")
    @RequiresPermission("promotionConfig:query")
    public ResponseDTO<List<PromotionConfigOptionDTO>> queryOptionList() {
        return ResponseDTO.ok(Service.queryOptionList());
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @RequiresPermission("promotionConfig:add")
    public ResponseDTO<String> add(@RequestBody @Valid PromotionConfigAddForm addForm) {
        Service.add(SolvelaBeanUtil.copy(addForm, PromotionConfigAddCommand.class));
        return ResponseDTO.ok();
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @RequiresPermission("promotionConfig:update")
    public ResponseDTO<String> update(@RequestBody @Valid PromotionConfigUpdateForm updateForm) {
        Service.update(SolvelaBeanUtil.copy(updateForm, PromotionConfigUpdateCommand.class));
        return ResponseDTO.ok();
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @RequiresPermission("promotionConfig:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        Service.batchDelete(idList);
        return ResponseDTO.ok();
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @RequiresPermission("promotionConfig:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        Service.delete(id);
        return ResponseDTO.ok();
    }
}
