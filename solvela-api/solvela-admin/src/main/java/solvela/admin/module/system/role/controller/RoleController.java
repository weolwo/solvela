package solvela.admin.module.system.role.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import solvela.admin.constant.AdminSwaggerTagConst;
import solvela.admin.module.system.role.domain.form.RoleAddForm;
import solvela.admin.module.system.role.domain.form.RoleUpdateForm;
import solvela.admin.module.system.role.domain.vo.RoleVO;
import solvela.admin.module.system.role.service.RoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色
 *
 * @Author 1024创新实验室: 胡克
 * @Date 2021-12-14 19:40:28
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@RestController
@Tag(name = AdminSwaggerTagConst.System.SYSTEM_ROLE)
public class RoleController {

    @Resource
    private RoleService roleService;

    @Operation(summary = "添加角色 @author 卓大")
    @PostMapping("/role/add")
    @RequiresPermission("system:role:addProposal")
    public void addRole(@Valid @RequestBody RoleAddForm roleAddForm) {
        roleService.addRole(roleAddForm);
    }

    @Operation(summary = "删除角色 @author 卓大")
    @GetMapping("/role/delete/{roleId}")
    @RequiresPermission("system:role:delete")
    public void deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
    }

    @Operation(summary = "更新角色 @author 卓大")
    @PostMapping("/role/update")
    @RequiresPermission("system:role:update")
    public void updateRole(@Valid @RequestBody RoleUpdateForm roleUpdateDTO) {
        roleService.updateRole(roleUpdateDTO);
    }

    @Operation(summary = "获取角色数据 @author 卓大")
    @GetMapping("/role/get/{roleId}")
    public RoleVO getRole(@PathVariable("roleId") Long roleId) {
        return roleService.getRoleById(roleId);
    }

    @Operation(summary = "获取所有角色 @author 卓大")
    @GetMapping("/role/getAll")
    public List<RoleVO> getAllRole() {
        return roleService.getAllRole();
    }

}
