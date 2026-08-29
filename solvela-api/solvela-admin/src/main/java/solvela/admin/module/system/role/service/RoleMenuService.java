package solvela.admin.module.system.role.service;

import solvela.exception.BusinessException;
import jakarta.annotation.Resource;
import solvela.admin.module.system.menu.dao.MenuDao;
import solvela.admin.module.system.menu.domain.entity.MenuEntity;
import solvela.admin.module.system.menu.domain.vo.MenuSimpleTreeVO;
import solvela.admin.module.system.menu.domain.vo.MenuVO;
import solvela.admin.module.system.role.dao.RoleDao;
import solvela.admin.module.system.role.dao.RoleMenuDao;
import solvela.admin.module.system.role.domain.entity.RoleEntity;
import solvela.admin.module.system.role.domain.entity.RoleMenuEntity;
import solvela.admin.module.system.role.domain.form.RoleMenuUpdateForm;
import solvela.admin.module.system.role.domain.vo.RoleMenuTreeVO;
import solvela.admin.module.system.role.manager.RoleMenuManager;
import solvela.code.UserErrorCode;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCollectionUtil;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色-菜单
 *
 * @Author 1024创新实验室: 善逸
 * @Date 2021-10-22 23:17:47
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class RoleMenuService {

    @Resource
    private RoleDao roleDao;
    @Resource
    private RoleMenuDao roleMenuDao;
    @Resource
    private RoleMenuManager roleMenuManager;
    @Resource
    private MenuDao menuDao;

    /**
     * 更新角色权限
     *
     */
    public void updateRoleMenu(RoleMenuUpdateForm roleMenuUpdateForm) {
        //查询角色是否存在
        Long roleId = roleMenuUpdateForm.getRoleId();
        RoleEntity roleEntity = roleDao.selectById(roleId);
        if (null == roleEntity) {
            throw new BusinessException(UserErrorCode.DATA_NOT_EXIST);
        }
        List<RoleMenuEntity> roleMenuEntityList = new ArrayList<>();
        RoleMenuEntity roleMenuEntity;
        for (Long menuId : roleMenuUpdateForm.getMenuIdList()) {
            roleMenuEntity = new RoleMenuEntity();
            roleMenuEntity.setRoleId(roleId);
            roleMenuEntity.setMenuId(menuId);
            roleMenuEntityList.add(roleMenuEntity);
        }
        roleMenuManager.updateRoleMenu(roleMenuUpdateForm.getRoleId(), roleMenuEntityList);
    }

    /**
     * 根据角色id集合，查询其所有的菜单权限
     *
     */
    public List<MenuVO> getMenuList(List<Long> roleIdList, Boolean administratorFlag) {
        //管理员返回所有菜单
        if(administratorFlag){
            List<MenuEntity> menuEntityList = roleMenuDao.selectMenuListByRoleIdList(new ArrayList<>(), false);
            return SolvelaBeanUtil.copyList(menuEntityList, MenuVO.class);
        }
        //非管理员 无角色 返回空菜单
        if (SolvelaCollectionUtil.isEmpty(roleIdList)) {
            return new ArrayList<>();
        }
        List<MenuEntity> menuEntityList = roleMenuDao.selectMenuListByRoleIdList(roleIdList, false);
        return SolvelaBeanUtil.copyList(menuEntityList, MenuVO.class);
    }


    /**
     * 获取角色关联菜单权限
     *
     */
    public RoleMenuTreeVO getRoleSelectedMenu(Long roleId) {
        RoleMenuTreeVO res = new RoleMenuTreeVO();
        res.setRoleId(roleId);
        //查询角色ID选择的菜单权限
        List<Long> selectedMenuId = roleMenuDao.queryMenuIdByRoleId(roleId);
        res.setSelectedMenuId(selectedMenuId);
        //查询菜单权限
        List<MenuVO> menuVOList = menuDao.queryMenuList(Boolean.FALSE, Boolean.FALSE, null);
        Map<Long, List<MenuVO>> parentMap = menuVOList.stream().collect(Collectors.groupingBy(MenuVO::getParentId, Collectors.toList()));
        List<MenuSimpleTreeVO> menuTreeList = this.buildMenuTree(parentMap, NumberUtils.LONG_ZERO);
        res.setMenuTreeList(menuTreeList);
        return res;
    }

    /**
     * 构建菜单树
     *
     */
    private List<MenuSimpleTreeVO> buildMenuTree(Map<Long, List<MenuVO>> parentMap, Long parentId) {
        // 获取本级菜单树List
        List<MenuSimpleTreeVO> res = parentMap.getOrDefault(parentId, new ArrayList<>()).stream()
                .map(e -> SolvelaBeanUtil.copy(e, MenuSimpleTreeVO.class)).collect(Collectors.toList());
        // 循环遍历下级菜单
        res.forEach(e -> {
            e.setChildren(this.buildMenuTree(parentMap, e.getMenuId()));
        });
        return res;
    }
}
