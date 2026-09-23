package solvela.admin.module.mall.gradeprice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.mall.gradeprice.domain.form.MallGradePriceForm;
import solvela.base.util.SolvelaBeanUtil;
import solvela.mall.MallGradePrice;
import solvela.mall.commodity.service.MallGradePriceService;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 商城-单品覆盖价 Controller。
 *
 * <h3>🔴 权限点跟着商品编辑走，不另开一个</h3>
 * 能改商品价的人本来就能改这里的价 —— 拆成两个权限，会出现「能把商品从 100 改成 1，
 * 但不能给白金配 88」这种没有任何意义的授权组合。
 * <b>查询也挂 update 而不是 query</b>：这张表上没有一行是给人「看看」的，
 * 看它的唯一理由就是要改它。
 *
 * @author alaric
 * @date 2026-09-23
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "商城-单品覆盖价")
@RequestMapping("/mallGradePrice")
public class MallGradePriceController {

    private final MallGradePriceService mallGradePriceService;

    @Operation(summary = "一件商品的全部覆盖价 @author alaric")
    @GetMapping("/list/{commodityId}")
    @RequiresPermission("mallCommodity:update")
    public List<MallGradePrice> list(@PathVariable Long commodityId) {
        return mallGradePriceService.listByCommodity(commodityId);
    }

    @Operation(summary = "新增/编辑一行覆盖价 @author alaric")
    @PostMapping("/save")
    @RequiresPermission("mallCommodity:update")
    public void save(@RequestBody @Valid MallGradePriceForm form) {
        mallGradePriceService.save(SolvelaBeanUtil.copy(form, MallGradePrice.class),
                CurrentEmployee.nameOrNull());
    }

    @Operation(summary = "删除一行覆盖价（这一档落回等级折扣率） @author alaric")
    @GetMapping("/delete/{id}")
    @RequiresPermission("mallCommodity:update")
    public void delete(@PathVariable Long id) {
        mallGradePriceService.delete(id, CurrentEmployee.nameOrNull());
    }
}
