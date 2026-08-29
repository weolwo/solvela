package solvela.admin.module.system.menu.service;

import solvela.exception.BusinessException;
import jakarta.annotation.Resource;
import solvela.admin.module.system.menu.constant.MenuTypeEnum;
import solvela.admin.module.system.menu.dao.MenuDao;
import solvela.admin.module.system.menu.domain.entity.MenuEntity;
import solvela.admin.module.system.menu.domain.form.MenuAddForm;
import solvela.admin.module.system.menu.domain.form.MenuBaseForm;
import solvela.admin.module.system.menu.domain.form.MenuUpdateForm;
import solvela.admin.module.system.menu.domain.vo.MenuTreeVO;
import solvela.admin.module.system.menu.domain.vo.MenuVO;
import solvela.code.SystemErrorCode;
import solvela.web.config.RequestUrl;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaStringUtil;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;

/**
 * 菜单
 *
 * @Author 1024创新实验室: 善逸
 * @Date 2022-03-08 22:15:09
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class MenuService {

    @Resource
    private MenuDao menuDao;

    @Resource
    private List<RequestUrl> authUrl;

    /**
     * 添加菜单
     *
     */
    public synchronized void addMenu(MenuAddForm menuAddForm) {
        // 校验菜单名称
        if (this.validateMenuName(menuAddForm)) {
            throw new BusinessException("菜单名称已存在");
        }
        // 校验前端权限字符串
        if (this.validateWebPerms(menuAddForm)) {
            throw new BusinessException("前端权限字符串已存在");
        }
        MenuEntity menuEntity = SolvelaBeanUtil.copy(menuAddForm, MenuEntity.class);
        menuDao.insert(menuEntity);
    }

    /**
     * 更新菜单
     *
     */
    public synchronized void updateMenu(MenuUpdateForm menuUpdateForm) {
        //校验菜单是否存在
        MenuEntity selectMenu = menuDao.selectById(menuUpdateForm.getMenuId());
        if (selectMenu == null) {
            throw new BusinessException("菜单不存在");
        }
        if (selectMenu.getDeletedFlag()) {
            throw new BusinessException("菜单已被删除");
        }
        //校验菜单名称
        if (this.validateMenuName(menuUpdateForm)) {
            throw new BusinessException("菜单名称已存在");
        }
        // 校验前端权限字符串
        if (this.validateWebPerms(menuUpdateForm)) {
            throw new BusinessException("前端权限字符串已存在");
        }
        if (menuUpdateForm.getMenuId().equals(menuUpdateForm.getParentId())) {
            throw new BusinessException("上级菜单不能为自己");
        }
        MenuEntity menuEntity = SolvelaBeanUtil.copy(menuUpdateForm, MenuEntity.class);
        menuDao.updateById(menuEntity);
    }


    /**
     * 批量删除菜单
     *
     */
    public synchronized void batchDeleteMenu(List<Long> menuIdList, Long employeeId) {
        if (CollectionUtils.isEmpty(menuIdList)) {
            throw new BusinessException("所选菜单不能为空");
        }
        menuDao.deleteByMenuIdList(menuIdList, employeeId, Boolean.TRUE);
        //孩子节点也需要删除
        this.recursiveDeleteChildren(menuIdList, employeeId);
    }

    private void recursiveDeleteChildren(List<Long> menuIdList, Long employeeId) {
        List<Long> childrenMenuIdList = menuDao.selectMenuIdByParentIdList(menuIdList);
        if (CollectionUtils.isEmpty(childrenMenuIdList)) {
            return;
        }
        menuDao.deleteByMenuIdList(childrenMenuIdList, employeeId, Boolean.TRUE);
        recursiveDeleteChildren(childrenMenuIdList, employeeId);
    }

    /**
     * 校验菜单名称
     *
     */
    public <T extends MenuBaseForm> Boolean validateMenuName(T menuDTO) {
        MenuEntity menu = menuDao.getByMenuName(menuDTO.getMenuName(), menuDTO.getParentId(), Boolean.FALSE);
        if (menuDTO instanceof MenuAddForm) {
            return menu != null;
        }
        if (menuDTO instanceof MenuUpdateForm) {
            Long menuId = ((MenuUpdateForm) menuDTO).getMenuId();
            return menu != null && menu.getMenuId().longValue() != menuId.longValue();
        }
        return true;
    }

    /**
     * 校验前端权限字符串
     *
     * @return true 重复 false 未重复
     */
    public <T extends MenuBaseForm> Boolean validateWebPerms(T menuDTO) {
        if (SolvelaStringUtil.isEmpty(menuDTO.getWebPerms())) {
            return false;
        }

        MenuEntity menu = menuDao.getByWebPerms(menuDTO.getWebPerms(), Boolean.FALSE);
        if (menuDTO instanceof MenuAddForm) {
            return menu != null;
        }
        if (menuDTO instanceof MenuUpdateForm) {
            Long menuId = ((MenuUpdateForm) menuDTO).getMenuId();
            return menu != null && menu.getMenuId().longValue() != menuId.longValue();
        }
        return true;
    }

    /**
     * 查询菜单列表
     *
     */
    public List<MenuVO> queryMenuList(Boolean disabledFlag) {
        List<MenuVO> menuVOList = menuDao.queryMenuList(Boolean.FALSE, disabledFlag, null);
        //根据ParentId进行分组
        Map<Long, List<MenuVO>> parentMap = menuVOList.stream().collect(Collectors.groupingBy(MenuVO::getParentId, Collectors.toList()));
        return this.filterNoParentMenu(parentMap, NumberUtils.LONG_ZERO);
    }

    /**
     * 过滤没有上级菜单的菜单列表
     *
     */
    private List<MenuVO> filterNoParentMenu(Map<Long, List<MenuVO>> parentMap, Long parentId) {
        // 获取本级菜单树List
        List<MenuVO> res = parentMap.getOrDefault(parentId, new ArrayList<>());
        List<MenuVO> childMenu = new ArrayList<>();
        // 循环遍历下级菜单
        res.forEach(e -> {
            List<MenuVO> menuList = this.filterNoParentMenu(parentMap, e.getMenuId());
            childMenu.addAll(menuList);
        });
        res.addAll(childMenu);
        return res;
    }

    /**
     * 查询菜单树
     *
     * @param onlyMenu 不查询功能点
     */
    public List<MenuTreeVO> queryMenuTree(Boolean onlyMenu) {
        List<Integer> menuTypeList = new ArrayList<>();
        if (onlyMenu) {
            menuTypeList = List.of(MenuTypeEnum.CATALOG.getValue(), MenuTypeEnum.MENU.getValue());
        }
        List<MenuVO> menuVOList = menuDao.queryMenuList(Boolean.FALSE, null, menuTypeList);
        //根据ParentId进行分组
        Map<Long, List<MenuVO>> parentMap = menuVOList.stream().collect(Collectors.groupingBy(MenuVO::getParentId, Collectors.toList()));
        List<MenuTreeVO> menuTreeVOList = this.buildMenuTree(parentMap, NumberUtils.LONG_ZERO);
        return menuTreeVOList;
    }

    /**
     * 构建菜单树
     *
     */
    List<MenuTreeVO> buildMenuTree(Map<Long, List<MenuVO>> parentMap, Long parentId) {
        // 获取本级菜单树List
        List<MenuTreeVO> res = parentMap.getOrDefault(parentId, new ArrayList<>()).stream()
                .map(e -> SolvelaBeanUtil.copy(e, MenuTreeVO.class)).collect(Collectors.toList());
        // 循环遍历下级菜单
        res.forEach(e -> {
            e.setChildren(this.buildMenuTree(parentMap, e.getMenuId()));
        });
        return res;
    }

    /**
     * 查询菜单详情
     *
     */
    public MenuVO getMenuDetail(Long menuId) {
        //校验菜单是否存在
        MenuEntity selectMenu = menuDao.selectById(menuId);
        if (selectMenu == null) {
            throw new BusinessException(SystemErrorCode.SYSTEM_ERROR, "菜单不存在");
        }
        if (selectMenu.getDeletedFlag()) {
            throw new BusinessException(SystemErrorCode.SYSTEM_ERROR, "菜单已被删除");
        }
        MenuVO menuVO = SolvelaBeanUtil.copy(selectMenu, MenuVO.class);
        return menuVO;
    }

    /**
     * 获取系统所有请求路径
     */
    public List<RequestUrl> getAuthUrl() {
        return authUrl;
    }

}
