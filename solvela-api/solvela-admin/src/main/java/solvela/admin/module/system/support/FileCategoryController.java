package solvela.admin.module.system.support;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import solvela.admin.module.system.support.SupportBaseController;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.util.SolvelaRequestUtil;
import solvela.base.constant.SwaggerTagConst;
import solvela.base.module.support.file.domain.entity.FileCategoryEntity;
import solvela.base.module.support.file.domain.vo.FileCategoryVO;
import solvela.base.module.support.file.service.FileCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件分类管理。
 *
 * <p>列表接口<b>不挂权限点</b>：上传组件和素材库筛选器都要拿分类下拉，
 * 挂上之后没有「文件管理」权限的人连传附件都做不了。增删改排序才需要权限。
 *
 * @Date 2026-08-10
 */
@RestController
@Tag(name = SwaggerTagConst.Support.FILE)
public class FileCategoryController extends SupportBaseController {

    @Resource
    private FileCategoryService fileCategoryService;

    @Operation(summary = "文件分类列表（按展示顺序，带文件数） @author 1024")
    @GetMapping("/file/category/list")
    public ResponseDTO<List<FileCategoryVO>> list() {
        return ResponseDTO.ok(fileCategoryService.listWithCount());
    }

    @Operation(summary = "新建文件分类 @author 1024")
    @PostMapping("/file/category/add")
    @SaCheckPermission("support:file:query")
    public ResponseDTO<FileCategoryEntity> add(@RequestBody @Valid FileCategoryEntity form) {
        return ResponseDTO.ok(fileCategoryService.add(form, SolvelaRequestUtil.getRequestUser()));
    }

    @Operation(summary = "更新文件分类 @author 1024")
    @PostMapping("/file/category/update")
    @SaCheckPermission("support:file:query")
    public ResponseDTO<String> update(@RequestBody @Valid FileCategoryEntity form) {
        fileCategoryService.update(form, SolvelaRequestUtil.getRequestUser());
        return ResponseDTO.ok();
    }

    @Operation(summary = "删除文件分类（内置分类与非空分类不允许删） @author 1024")
    @GetMapping("/file/category/delete/{categoryId}")
    @SaCheckPermission("support:file:query")
    public ResponseDTO<String> delete(@PathVariable Long categoryId) {
        fileCategoryService.delete(categoryId);
        return ResponseDTO.ok();
    }

    /**
     * 拖拽排序：传排好序的分类 ID 列表。
     *
     * <p>全量重排，几十个分类不需要稀疏整数那套；整个重排在一个事务里，
     * 中途失败不会留下一半新序一半旧序。
     */
    @Operation(summary = "文件分类拖拽排序 @author 1024")
    @PostMapping("/file/category/reorder")
    @SaCheckPermission("support:file:query")
    public ResponseDTO<String> reorder(@RequestBody List<Long> orderedIds) {
        fileCategoryService.reorder(orderedIds, SolvelaRequestUtil.getRequestUser());
        return ResponseDTO.ok();
    }
}
