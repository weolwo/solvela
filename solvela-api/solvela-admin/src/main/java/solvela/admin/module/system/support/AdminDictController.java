package solvela.admin.module.system.support;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import solvela.admin.module.system.support.SupportBaseController;
import solvela.base.domain.PageResult;
import solvela.base.domain.ValidateList;
import solvela.base.constant.SwaggerTagConst;
import solvela.admin.module.system.dict.domain.form.*;
import solvela.admin.module.system.dict.domain.vo.DictDataVO;
import solvela.admin.module.system.dict.domain.vo.DictVO;
import solvela.admin.module.system.dict.service.DictService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典 Controller
 *
 * @Author 1024创新实验室-主任-卓大
 * @Date 2025-03-25 22:25:04
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Tag(name = SwaggerTagConst.Support.DICT)
@RestController
public class AdminDictController extends SupportBaseController {

    @Resource
    private DictService dictService;

    // -------------------  获取全部数据 -------------------

    @Operation(summary = "获取全部数据（供前端缓存使用） @author 1024创新实验室-主任-卓大")
    @GetMapping("/dict/getAllDictData")
    public List<DictDataVO> getAll() {
        return dictService.getAll();
    }

    @Operation(summary = "获取所有字典code @author 1024创新实验室-主任-卓大")
    @GetMapping("/dict/getAllDict")
    public List<DictVO> getAllDict() {
        return dictService.getAllDict();
    }

    // -------------------  字典 -------------------

    @Operation(summary = "分页查询 @author 1024创新实验室-主任-卓大")
    @PostMapping("/dict/queryPage")
    @RequiresPermission("support:dict:query")
    public PageResult<DictVO> queryPage(@RequestBody @Valid DictQueryForm queryForm) {
        return dictService.queryPage(queryForm);
    }

    @Operation(summary = "添加 @author 1024创新实验室-主任-卓大")
    @PostMapping("/dict/add")
    @RequiresPermission("support:dict:addProposal")
    public void add(@RequestBody @Valid DictAddForm addForm) {
        dictService.add(addForm);
    }

    @Operation(summary = "更新 @author 1024创新实验室-主任-卓大")
    @PostMapping("/dict/update")
    @RequiresPermission("support:dict:update")
    public void update(@RequestBody @Valid DictUpdateForm updateForm) {
        dictService.update(updateForm);
    }

    @Operation(summary = "启用/禁用 @author 1024创新实验室-主任-卓大")
    @GetMapping("/dict/updateDisabled/{dictId}")
    @RequiresPermission("support:dict:updateDisabled")
    public void updateDisabled(@PathVariable Long dictId) {
        dictService.updateDisabled(dictId);
    }

    @Operation(summary = "批量删除 @author 1024创新实验室-主任-卓大")
    @PostMapping("/dict/batchDelete")
    @RequiresPermission("support:dict:delete")
    public void batchDelete(@RequestBody ValidateList<Long> idList) {
        dictService.batchDelete(idList);
    }

    @Operation(summary = "单个删除 @author 1024创新实验室-主任-卓大")
    @GetMapping("/dict/delete/{dictId}")
    @RequiresPermission("support:dict:delete")
    public void delete(@PathVariable Long dictId) {
        dictService.delete(dictId);
    }

    // -------------------  字典数据 -------------------

    @Operation(summary = "字典数据 分页查询 @author 1024创新实验室-主任-卓大")
    @GetMapping("/dict/dictData/queryDictData/{dictId}")
    @RequiresPermission("support:dictData:query")
    public List<DictDataVO> queryDictData(@PathVariable Long dictId) {
        return dictService.queryDictData(dictId);
    }

    @Operation(summary = "字典数据 启用/禁用 @author 1024创新实验室-主任-卓大")
    @GetMapping("/dict/dictData/updateDisabled/{dictDataId}")
    @RequiresPermission("support:dictData:updateDisabled")
    public void updateDictDataDisabled(@PathVariable Long dictDataId) {
        dictService.updateDictDataDisabled(dictDataId);
    }

    @Operation(summary = "字典数据 添加 @author 1024创新实验室-主任-卓大")
    @PostMapping("/dict/dictData/add")
    @RequiresPermission("support:dictData:addProposal")
    public void addDictData(@RequestBody @Valid DictDataAddForm addForm) {
        dictService.addDictData(addForm);
    }

    @Operation(summary = "字典数据 更新 @author 1024创新实验室-主任-卓大")
    @PostMapping("/dict/dictData/update")
    @RequiresPermission("support:dictData:update")
    public void updateDictData(@RequestBody @Valid DictDataUpdateForm updateForm) {
        dictService.updateDictData(updateForm);
    }

    @Operation(summary = "字典数据 批量删除 @author 1024创新实验室-主任-卓大")
    @PostMapping("/dict/dictData/batchDelete")
    @RequiresPermission("support:dictData:delete")
    public void batchDeleteDictData(@RequestBody ValidateList<Long> idList) {
        dictService.batchDeleteDictData(idList);
    }

    @Operation(summary = "字典数据 单个删除 @author 1024创新实验室-主任-卓大")
    @GetMapping("/dict/dictData/delete/{dictDataId}")
    @RequiresPermission("support:dictData:delete")
    public void deleteDictData(@PathVariable Long dictDataId) {
        dictService.deleteDictData(dictDataId);
    }

}
