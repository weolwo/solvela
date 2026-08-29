package solvela.admin.module.mall.category.controller;

import solvela.web.RequiresPermission;
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
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.domain.ValidateList;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.mall.category.domain.form.MallCategoryBatchSaveForm;
import solvela.mall.category.domain.command.MallCategoryBatchSaveCommand;
import solvela.admin.module.mall.category.domain.form.MallCategoryQueryForm;
import solvela.mall.category.domain.query.MallCategoryQuery;
import solvela.admin.module.mall.category.domain.form.MallCategorySaveForm;
import solvela.mall.category.domain.command.MallCategorySaveCommand;
import solvela.admin.module.mall.category.domain.vo.MallCategoryVO;
import solvela.mall.category.domain.dto.MallCategoryDTO;
import solvela.mall.category.service.MallCategoryService;

import java.util.List;

/**
 * 商城-商品分类 Controller
 *
 * <p>写操作只有 save 一个口子，生成器留下的 add / update 已删除：那两个表单一个强制要求
 * 传 id（新建时根本没有），一个只有 id 一个字段（什么都改不了），都不能用。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:28:16
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "商城-商品分类")
@RequestMapping("/mallCategory")
public class MallCategoryController {

    private final MallCategoryService mallCategoryService;

    @Operation(summary = "分页查询 @author weolwo")
    @PostMapping("/queryPage")
    @RequiresPermission("mallCategory:query")
    public PageResult<MallCategoryVO> queryPage(@RequestBody @Valid MallCategoryQueryForm queryForm) {
        PageResult<MallCategoryDTO> page = mallCategoryService.queryPage(SolvelaBeanUtil.copy(queryForm, MallCategoryQuery.class));
        return SolvelaPageUtil.convert2PageResult(page, MallCategoryVO.class);
    }

    /**
     * 全部分类（不分页）。管理端列表页用它自己拼两级树 —— 分页会把「父在第 2 页、
     * 子在第 1 页」这种拼不出树的情况带进来。
     */
    @Operation(summary = "全部分类（不分页），列表页拼树用 @author weolwo")
    @GetMapping("/queryAll")
    @RequiresPermission("mallCategory:query")
    public List<MallCategoryVO> queryAll() {
        return SolvelaBeanUtil.copyList(
                mallCategoryService.queryAll(), MallCategoryVO.class);
    }

    @Operation(summary = "启用中的分类列表：商品编辑页的分类下拉用 @author weolwo")
    @GetMapping("/enabledList")
    @RequiresPermission("mallCategory:query")
    public List<MallCategoryVO> enabledList() {
        return SolvelaBeanUtil.copyList(
                mallCategoryService.queryEnabledList(), MallCategoryVO.class);
    }

    /**
     * 保存（id 为空即新建）。权限点只挂 update：新建与编辑在业务上是同一个动作。
     */
    @Operation(summary = "保存分类，id为空即新建 @author weolwo")
    @PostMapping("/save")
    @RequiresPermission("mallCategory:update")
    public Long save(@RequestBody @Valid MallCategorySaveForm saveForm) {
        return mallCategoryService.save(SolvelaBeanUtil.copy(saveForm, MallCategorySaveCommand.class), CurrentEmployee.nameOrNull());
    }

    /**
     * 批量新建：一次建多个，可带一层子分类。
     *
     * <p>与 save 分开而不是让 save 支持数组：save 还要处理「编辑」，而编辑天然是单条的
     * （改一个分类的名字不会顺带改它兄弟的）。混在一起会让那个方法同时背两套语义。
     */
    @Operation(summary = "批量新建分类（可带一层子分类） @author weolwo")
    @PostMapping("/batchSave")
    @RequiresPermission("mallCategory:add")
    public Integer batchSave(@RequestBody @Valid MallCategoryBatchSaveForm batchSaveForm) {
        return mallCategoryService.batchSave(SolvelaBeanUtil.deepCopy(batchSaveForm, MallCategoryBatchSaveCommand.class), CurrentEmployee.nameOrNull());
    }

    @Operation(summary = "启用/停用 @author weolwo")
    @GetMapping("/updateStatus/{id}/{status}")
    @RequiresPermission("mallCategory:update")
    public void updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        mallCategoryService.updateStatus(id, status, CurrentEmployee.nameOrNull());
    }

    @Operation(summary = "批量删除 @author weolwo")
    @PostMapping("/batchDelete")
    @RequiresPermission("mallCategory:delete")
    public void batchDelete(@RequestBody ValidateList<Long> idList) {
        mallCategoryService.batchDelete(idList);
    }

    @Operation(summary = "单个删除 @author weolwo")
    @GetMapping("/delete/{id}")
    @RequiresPermission("mallCategory:delete")
    public void delete(@PathVariable Long id) {
        mallCategoryService.delete(id);
    }
}
