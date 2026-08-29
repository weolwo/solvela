package solvela.admin.module.system.position.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import solvela.admin.constant.AdminSwaggerTagConst;
import solvela.admin.module.system.position.domain.form.PositionAddForm;
import solvela.admin.module.system.position.domain.form.PositionQueryForm;
import solvela.admin.module.system.position.domain.form.PositionUpdateForm;
import solvela.admin.module.system.position.domain.vo.PositionVO;
import solvela.admin.module.system.position.service.PositionService;
import solvela.base.domain.PageResult;
import solvela.base.domain.ValidateList;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 职务表 Controller
 *
 * @Author kaiyun
 * @Date 2024-06-23 23:31:38
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

@RestController
@Tag(name = AdminSwaggerTagConst.System.SYSTEM_POSITION)
public class PositionController {

    @Resource
    private PositionService positionService;

    @Operation(summary = "分页查询 @author kaiyun")
    @PostMapping("/position/queryPage")
    public PageResult<PositionVO> queryPage(@RequestBody @Valid PositionQueryForm queryForm) {
        return positionService.queryPage(queryForm);
    }

    @Operation(summary = "添加 @author kaiyun")
    @PostMapping("/position/add")
    public void add(@RequestBody @Valid PositionAddForm addForm) {
        positionService.add(addForm);
    }

    @Operation(summary = "更新 @author kaiyun")
    @PostMapping("/position/update")
    public void update(@RequestBody @Valid PositionUpdateForm updateForm) {
        positionService.update(updateForm);
    }

    @Operation(summary = "批量删除 @author kaiyun")
    @PostMapping("/position/batchDelete")
    public void batchDelete(@RequestBody ValidateList<Long> idList) {
        positionService.batchDelete(idList);
    }

    @Operation(summary = "单个删除 @author kaiyun")
    @GetMapping("/position/delete/{positionId}")
    public void batchDelete(@PathVariable Long positionId) {
        positionService.delete(positionId);
    }


    @Operation(summary = "不分页查询 @author kaiyun")
    @GetMapping("/position/queryList")
    public List<PositionVO> queryList() {
        return positionService.queryList();
    }
}
