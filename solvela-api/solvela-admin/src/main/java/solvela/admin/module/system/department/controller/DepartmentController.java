package solvela.admin.module.system.department.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import solvela.admin.constant.AdminSwaggerTagConst;
import solvela.admin.module.system.department.domain.form.DepartmentAddForm;
import solvela.admin.module.system.department.domain.form.DepartmentUpdateForm;
import solvela.admin.module.system.department.domain.vo.DepartmentTreeVO;
import solvela.admin.module.system.department.domain.vo.DepartmentVO;
import solvela.admin.module.system.department.service.DepartmentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-01-12 20:37:48
 * @Wechat 卓大1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@RestController
@Tag(name = AdminSwaggerTagConst.System.SYSTEM_DEPARTMENT)
public class DepartmentController {

    @Resource
    private DepartmentService departmentService;

    @Operation(summary = "查询部门树形列表 @author 卓大")
    @GetMapping("/department/treeList")
    public List<DepartmentTreeVO> departmentTree() {
        return departmentService.departmentTree();
    }

    @Operation(summary = "添加部门 @author 卓大")
    @PostMapping("/department/add")
    @RequiresPermission("system:department:addProposal")
    public void addDepartment(@Valid @RequestBody DepartmentAddForm createDTO) {
        departmentService.addDepartment(createDTO);
    }

    @Operation(summary = "更新部门 @author 卓大")
    @PostMapping("/department/update")
    @RequiresPermission("system:department:update")
    public void updateDepartment(@Valid @RequestBody DepartmentUpdateForm updateDTO) {
        departmentService.updateDepartment(updateDTO);
    }

    @Operation(summary = "删除部门 @author 卓大")
    @GetMapping("/department/delete/{departmentId}")
    @RequiresPermission("system:department:delete")
    public void deleteDepartment(@PathVariable Long departmentId) {
        departmentService.deleteDepartment(departmentId);
    }

    @Operation(summary = "查询部门列表 @author 卓大")
    @GetMapping("/department/listAll")
    public List<DepartmentVO> listAll() {
        return departmentService.listAll();
    }

}
