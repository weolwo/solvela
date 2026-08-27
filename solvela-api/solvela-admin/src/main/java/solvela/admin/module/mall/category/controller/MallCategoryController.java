package solvela.admin.module.mall.category.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import solvela.base.domain.ResponseDTO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.domain.ValidateList;
import solvela.base.web.CurrentUser;
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
    @SaCheckPermission("mallCategory:query")
    public ResponseDTO<PageResult<MallCategoryVO>> queryPage(@RequestBody @Valid MallCategoryQueryForm queryForm) {
        PageResult<MallCategoryDTO> page = mallCategoryService.queryPage(SolvelaBeanUtil.copy(queryForm, MallCategoryQuery.class));
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, MallCategoryVO.class));
    }

    /**
     * 全部分类（不分页）。管理端列表页用它自己拼两级树 —— 分页会把「父在第 2 页、
     * 子在第 1 页」这种拼不出树的情况带进来。
     */
    @Operation(summary = "全部分类（不分页），列表页拼树用 @author weolwo")
    @GetMapping("/queryAll")
    @SaCheckPermission("mallCategory:query")
    public ResponseDTO<List<MallCategoryVO>> queryAll() {
        return ResponseDTO.ok(SolvelaBeanUtil.copyList(
                mallCategoryService.queryAll().getData(), MallCategoryVO.class));
    }

    @Operation(summary = "启用中的分类列表：商品编辑页的分类下拉用 @author weolwo")
    @GetMapping("/enabledList")
    @SaCheckPermission("mallCategory:query")
    public ResponseDTO<List<MallCategoryVO>> enabledList() {
        return ResponseDTO.ok(SolvelaBeanUtil.copyList(
                mallCategoryService.queryEnabledList().getData(), MallCategoryVO.class));
    }

    /**
     * 保存（id 为空即新建）。权限点只挂 update：新建与编辑在业务上是同一个动作。
     */
    @Operation(summary = "保存分类，id为空即新建 @author weolwo")
    @PostMapping("/save")
    @SaCheckPermission("mallCategory:update")
    public ResponseDTO<Long> save(@RequestBody @Valid MallCategorySaveForm saveForm) {
        return mallCategoryService.save(SolvelaBeanUtil.copy(saveForm, MallCategorySaveCommand.class), CurrentUser.orNull());
    }

    /**
     * 批量新建：一次建多个，可带一层子分类。
     *
     * <p>与 save 分开而不是让 save 支持数组：save 还要处理「编辑」，而编辑天然是单条的
     * （改一个分类的名字不会顺带改它兄弟的）。混在一起会让那个方法同时背两套语义。
     */
    @Operation(summary = "批量新建分类（可带一层子分类） @author weolwo")
    @PostMapping("/batchSave")
    @SaCheckPermission("mallCategory:add")
    public ResponseDTO<Integer> batchSave(@RequestBody @Valid MallCategoryBatchSaveForm batchSaveForm) {
        return mallCategoryService.batchSave(SolvelaBeanUtil.deepCopy(batchSaveForm, MallCategoryBatchSaveCommand.class), CurrentUser.orNull());
    }

    @Operation(summary = "启用/停用 @author weolwo")
    @GetMapping("/updateStatus/{id}/{status}")
    @SaCheckPermission("mallCategory:update")
    public ResponseDTO<String> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        return mallCategoryService.updateStatus(id, status, CurrentUser.orNull());
    }

    @Operation(summary = "批量删除 @author weolwo")
    @PostMapping("/batchDelete")
    @SaCheckPermission("mallCategory:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return mallCategoryService.batchDelete(idList);
    }

    @Operation(summary = "单个删除 @author weolwo")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("mallCategory:delete")
    public ResponseDTO<String> delete(@PathVariable Long id) {
        return mallCategoryService.delete(id);
    }
}
