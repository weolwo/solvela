package solvela.admin.module.system.login.manager;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.admin.constant.AdminCacheConst;
import solvela.admin.module.system.department.domain.vo.DepartmentVO;
import solvela.admin.module.system.department.service.DepartmentService;
import solvela.admin.module.system.employee.domain.entity.EmployeeEntity;
import solvela.admin.module.system.employee.service.EmployeeService;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.admin.module.system.login.domain.UserPermission;
import solvela.admin.module.system.menu.domain.vo.MenuVO;
import solvela.admin.module.system.role.domain.vo.RoleVO;
import solvela.admin.module.system.role.service.RoleEmployeeService;
import solvela.admin.module.system.role.service.RoleMenuService;
import solvela.base.constant.StringConst;
import solvela.admin.constant.UserTypeEnum;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.module.file.service.FileAssetService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录Manager
 *
 * @Author 1024创新实验室: 卓大
 * @Date 2025-05-03 22:56:34
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@Service
public class LoginManager {

    @Resource
    private DepartmentService departmentService;

    @Resource
    private FileAssetService fileAssetService;

    @Resource
    private EmployeeService employeeService;

    @Resource
    private RoleEmployeeService roleEmployeeService;

    @Resource
    private RoleMenuService roleMenuService;


    /**
     * 获取请求用户信息
     */
    @Cacheable(AdminCacheConst.Login.REQUEST_EMPLOYEE)
    public RequestEmployee getRequestEmployee(Long requestEmployeeId ) {
        if (requestEmployeeId == null) {
            return null;
        }
        // 员工基本信息
        EmployeeEntity employeeEntity = employeeService.getById(requestEmployeeId);
        if (employeeEntity == null) {
            return null;
        }

        return this.loadLoginInfo(employeeEntity);
    }

    /**
     * 获取登录的用户信息
     */
    @CachePut(value = AdminCacheConst.Login.REQUEST_EMPLOYEE, key = "#employeeEntity.employeeId")
    public RequestEmployee loadLoginInfo(EmployeeEntity employeeEntity) {
        // 基础信息
        RequestEmployee requestEmployee = SolvelaBeanUtil.copy(employeeEntity, RequestEmployee.class);
        requestEmployee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);

        // 部门信息
        DepartmentVO department = departmentService.getDepartmentById(employeeEntity.getDepartmentId());
        requestEmployee.setDepartmentName(null == department ? StringConst.EMPTY : department.getDepartmentName());

        // 头像信息
        String avatar = employeeEntity.getAvatar();
        if (StringUtils.isNotBlank(avatar)) {
            // 头像存的是 storageKey，展示时才换算成 URL。查不到就保持原值，
            // 让"头像不见了"表现为一张裂图而不是登录直接失败
            String avatarUrl = fileAssetService.urlByStorageKeys(avatar);
            if (StringUtils.isNotBlank(avatarUrl)) {
                requestEmployee.setAvatar(avatarUrl);
            }
        }
        return requestEmployee;
    }


    /**
     * 获取用户的权限（包含 角色列表、权限列表）
     */
    @Cacheable(AdminCacheConst.Login.USER_PERMISSION)
    public UserPermission getUserPermission(Long employeeId) {
        if(null == employeeId){
            return null;
        }

        return this.loadUserPermission(employeeId);
    }

    /**
     * 获取用户的权限（包含 角色列表、权限列表）
     */
    @CachePut(AdminCacheConst.Login.USER_PERMISSION)
    public UserPermission loadUserPermission(Long employeeId) {
        // 角色列表
        List<RoleVO> roleList = roleEmployeeService.getRoleIdList(employeeId);
        Set<String> roleCodeSet = roleList.stream().map(RoleVO::getRoleCode).collect(Collectors.toSet());

        // 前端菜单和功能点清单
        EmployeeEntity employeeEntity = employeeService.getById(employeeId);
        List<MenuVO> menuAndPointsList = roleMenuService.getMenuList(
                roleList.stream().map(RoleVO::getRoleId).toList(), employeeEntity.getAdministratorFlag());

        // 权限列表：一个菜单的 apiPerms 是逗号分隔的多个权限点
        Set<String> permissionSet = new HashSet<>();
        for (MenuVO menu : menuAndPointsList) {
            if (menu.getPermsType() == null || StringUtils.isEmpty(menu.getApiPerms())) {
                continue;
            }
            permissionSet.addAll(Arrays.asList(menu.getApiPerms().split(",")));
        }

        return new UserPermission(List.copyOf(permissionSet), List.copyOf(roleCodeSet));
    }


    /**
     * 清除用户权限
     */
    @CacheEvict(value = AdminCacheConst.Login.USER_PERMISSION)
    public void clearUserPermission(Long employeeId) {

    }

    /**
     * 清除用户登录信息
     */
    @CacheEvict(value = AdminCacheConst.Login.REQUEST_EMPLOYEE)
    public void clearUserLoginInfo(Long employeeId) {

    }


}
