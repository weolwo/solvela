package solvela.admin.module.risk.promotionconfig.controller;

import solvela.base.domain.ValidateList;
import solvela.risk.PromotionConfig;
import solvela.risk.promotionconfig.domain.form.PromotionConfigAddForm;
import solvela.risk.promotionconfig.domain.form.PromotionConfigQueryForm;
import solvela.risk.promotionconfig.domain.form.PromotionConfigUpdateForm;
import solvela.risk.promotionconfig.domain.vo.PromotionConfigOptionVO;
import solvela.risk.promotionconfig.domain.vo.PromotionConfigVO;
import solvela.risk.promotionconfig.service.PromotionConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import solvela.base.domain.ResponseDTO;
import solvela.base.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
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
    @SaCheckPermission("promotionConfig:query")
    public ResponseDTO<PageResult<PromotionConfigVO>> queryPage(@RequestBody @Valid PromotionConfigQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "优惠配置下拉列表（全量启用中，前端按 prizeType 分组做级联）")
    @GetMapping("/optionList")
    @SaCheckPermission("promotionConfig:query")
    public ResponseDTO<List<PromotionConfigOptionVO>> queryOptionList() {
        return Service.queryOptionList();
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("promotionConfig:add")
    public ResponseDTO<String> add(@RequestBody @Valid PromotionConfigAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("promotionConfig:update")
    public ResponseDTO<String> update(@RequestBody @Valid PromotionConfigUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("promotionConfig:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("promotionConfig:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
