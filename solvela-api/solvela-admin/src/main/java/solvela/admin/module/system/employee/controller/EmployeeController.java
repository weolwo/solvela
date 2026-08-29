package solvela.admin.module.system.employee.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import solvela.admin.constant.AdminSwaggerTagConst;
import solvela.admin.module.system.employee.domain.form.*;
import solvela.admin.module.system.employee.domain.vo.EmployeeVO;
import solvela.admin.module.system.employee.service.EmployeeService;
import solvela.base.domain.PageResult;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.system.apiencrypt.annotation.ApiDecrypt;
import solvela.admin.module.system.securityprotect.service.Level3ProtectConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2021-12-09 22:57:49
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@RestController
@Tag(name = AdminSwaggerTagConst.System.SYSTEM_EMPLOYEE)
public class EmployeeController {

    @Resource
    private EmployeeService employeeService;

    @Resource
    private Level3ProtectConfigService level3ProtectConfigService;

    @PostMapping("/employee/query")
    @Operation(summary = "员工管理查询 @author 卓大")
    public PageResult<EmployeeVO> query(@Valid @RequestBody EmployeeQueryForm query) {
        return employeeService.queryEmployee(query);
    }

    @Operation(summary = "添加员工(返回添加员工的密码) @author 卓大")
    @PostMapping("/employee/add")
    @RequiresPermission("system:employee:addProposal")
    public String addEmployee(@Valid @RequestBody EmployeeAddForm employeeAddForm) {
        return employeeService.addEmployee(employeeAddForm);
    }

    @Operation(summary = "更新员工 @author 卓大")
    @PostMapping("/employee/update")
    @RequiresPermission("system:employee:update")
    public void updateEmployee(@Valid @RequestBody EmployeeUpdateForm employeeUpdateForm) {
        employeeService.updateEmployee(employeeUpdateForm);
    }

    @Operation(summary = "更新员工个人中心信息 @author 善逸")
    @PostMapping("/employee/update/center")
    public void updateCenter(@Valid @RequestBody EmployeeUpdateCenterForm updateCenterForm) {
        updateCenterForm.setEmployeeId(CurrentEmployee.idOrNull());
        employeeService.updateCenter(updateCenterForm);
    }

    @Operation(summary = "更新登录人头像 @author 善逸")
    @PostMapping("/employee/update/avatar")
    public void updateAvatar(@Valid @RequestBody EmployeeUpdateAvatarForm employeeUpdateAvatarForm) {
        employeeUpdateAvatarForm.setEmployeeId(CurrentEmployee.idOrNull());
        employeeService.updateAvatar(employeeUpdateAvatarForm);
    }

    @Operation(summary = "更新员工禁用/启用状态 @author 卓大")
    @GetMapping("/employee/update/disabled/{employeeId}")
    @RequiresPermission("system:employee:disabled")
    public void updateDisableFlag(@PathVariable Long employeeId) {
        employeeService.updateDisableFlag(employeeId);
    }

    @Operation(summary = "批量删除员工 @author 卓大")
    @PostMapping("/employee/update/batch/delete")
    @RequiresPermission("system:employee:delete")
    public void batchUpdateDeleteFlag(@RequestBody List<Long> employeeIdList) {
        employeeService.batchUpdateDeleteFlag(employeeIdList);
    }

    @Operation(summary = "批量调整员工部门 @author 卓大")
    @PostMapping("/employee/update/batch/department")
    @RequiresPermission("system:employee:department:update")
    public void batchUpdateDepartment(@Valid @RequestBody EmployeeBatchUpdateDepartmentForm batchUpdateDepartmentForm) {
        employeeService.batchUpdateDepartment(batchUpdateDepartmentForm);
    }

    @Operation(summary = "修改密码 @author 卓大")
    @PostMapping("/employee/update/password")
    @ApiDecrypt
    public void updatePassword(@Valid @RequestBody EmployeeUpdatePasswordForm updatePasswordForm) {
        updatePasswordForm.setEmployeeId(CurrentEmployee.idOrNull());
        employeeService.updatePassword(CurrentEmployee.orNull(), updatePasswordForm);
    }

    @Operation(summary = "获取密码复杂度 @author 卓大")
    @GetMapping("/employee/getPasswordComplexityEnabled")
    @ApiDecrypt
    public Boolean getPasswordComplexityEnabled() {
        return level3ProtectConfigService.isPasswordComplexityEnabled();
    }

    @Operation(summary = "重置员工密码 @author 卓大")
    @GetMapping("/employee/update/password/reset/{employeeId}")
    @RequiresPermission("system:employee:password:reset")
    public String resetPassword(@PathVariable Long employeeId) {
        return employeeService.resetPassword(employeeId);
    }

    @Operation(summary = "查询员工-根据部门id @author 卓大")
    @GetMapping("/employee/getAllEmployeeByDepartmentId/{departmentId}")
    public List<EmployeeVO> getAllEmployeeByDepartmentId(@PathVariable Long departmentId) {
        return employeeService.getAllEmployeeByDepartmentId(departmentId);
    }

    @Operation(summary = "查询所有员工 @author 卓大")
    @GetMapping("/employee/queryAll")
    public List<EmployeeVO> queryAllEmployee(@RequestParam(value = "disabledFlag", required = false) Boolean disabledFlag) {
        return employeeService.queryAllEmployee(disabledFlag);
    }

}
