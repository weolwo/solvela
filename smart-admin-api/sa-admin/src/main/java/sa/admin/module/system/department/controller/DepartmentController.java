package sa.admin.module.system.department.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import sa.admin.constant.AdminSwaggerTagConst;
import sa.admin.module.system.department.domain.form.DepartmentAddForm;
import sa.admin.module.system.department.domain.form.DepartmentUpdateForm;
import sa.admin.module.system.department.domain.vo.DepartmentTreeVO;
import sa.admin.module.system.department.domain.vo.DepartmentVO;
import sa.admin.module.system.department.service.DepartmentService;
import sa.base.common.domain.ResponseDTO;
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
    public ResponseDTO<List<DepartmentTreeVO>> departmentTree() {
        return departmentService.departmentTree();
    }

    @Operation(summary = "添加部门 @author 卓大")
    @PostMapping("/department/add")
    @SaCheckPermission("system:department:addProposal")
    public ResponseDTO<String> addDepartment(@Valid @RequestBody DepartmentAddForm createDTO) {
        return departmentService.addDepartment(createDTO);
    }

    @Operation(summary = "更新部门 @author 卓大")
    @PostMapping("/department/update")
    @SaCheckPermission("system:department:update")
    public ResponseDTO<String> updateDepartment(@Valid @RequestBody DepartmentUpdateForm updateDTO) {
        return departmentService.updateDepartment(updateDTO);
    }

    @Operation(summary = "删除部门 @author 卓大")
    @GetMapping("/department/delete/{departmentId}")
    @SaCheckPermission("system:department:delete")
    public ResponseDTO<String> deleteDepartment(@PathVariable Long departmentId) {
        return departmentService.deleteDepartment(departmentId);
    }

    @Operation(summary = "查询部门列表 @author 卓大")
    @GetMapping("/department/listAll")
    public ResponseDTO<List<DepartmentVO>> listAll() {
        return ResponseDTO.ok(departmentService.listAll());
    }

}
