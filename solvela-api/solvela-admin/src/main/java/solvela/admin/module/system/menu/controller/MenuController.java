package solvela.admin.module.system.menu.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import solvela.admin.constant.AdminSwaggerTagConst;
import solvela.admin.module.system.menu.domain.form.MenuAddForm;
import solvela.admin.module.system.menu.domain.form.MenuUpdateForm;
import solvela.admin.module.system.menu.domain.vo.MenuTreeVO;
import solvela.admin.module.system.menu.domain.vo.MenuVO;
import solvela.admin.module.system.menu.service.MenuService;
import solvela.web.config.RequestUrl;
import solvela.web.ResponseDTO;
import solvela.admin.auth.CurrentEmployee;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单
 *
 * @Author 1024创新实验室: 善逸
 * @Date 2022-03-06 22:04:37
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@RestController
@Tag(name = AdminSwaggerTagConst.System.SYSTEM_MENU)
public class MenuController {

    @Resource
    private MenuService menuService;

    @Operation(summary = "添加菜单 @author 卓大")
    @PostMapping("/menu/add")
    @RequiresPermission("system:menu:addProposal")
    public ResponseDTO<String> addMenu(@RequestBody @Valid MenuAddForm menuAddForm) {
        menuAddForm.setCreateUserId(CurrentEmployee.idOrNull());
        return menuService.addMenu(menuAddForm);
    }

    @Operation(summary = "更新菜单 @author 卓大")
    @PostMapping("/menu/update")
    @RequiresPermission("system:menu:update")
    public ResponseDTO<String> updateMenu(@RequestBody @Valid MenuUpdateForm menuUpdateForm) {
        menuUpdateForm.setUpdateUserId(CurrentEmployee.idOrNull());
        return menuService.updateMenu(menuUpdateForm);
    }

    @Operation(summary = "批量删除菜单 @author 卓大")
    @GetMapping("/menu/batchDelete")
    @RequiresPermission("system:menu:batchDelete")
    public ResponseDTO<String> batchDeleteMenu(@RequestParam("menuIdList") List<Long> menuIdList) {
        return menuService.batchDeleteMenu(menuIdList, CurrentEmployee.idOrNull());
    }

    @Operation(summary = "查询菜单列表 @author 卓大")
    @GetMapping("/menu/query")
    public ResponseDTO<List<MenuVO>> queryMenuList() {
        return ResponseDTO.ok(menuService.queryMenuList(null));
    }

    @Operation(summary = "查询菜单详情 @author 卓大")
    @GetMapping("/menu/detail/{menuId}")
    public ResponseDTO<MenuVO> getMenuDetail(@PathVariable Long menuId) {
        return menuService.getMenuDetail(menuId);
    }

    @Operation(summary = "查询菜单树 @author 卓大")
    @GetMapping("/menu/tree")
    public ResponseDTO<List<MenuTreeVO>> queryMenuTree(@RequestParam("onlyMenu") Boolean onlyMenu) {
        return menuService.queryMenuTree(onlyMenu);
    }

    @Operation(summary = "获取所有请求路径 @author 卓大")
    @GetMapping("/menu/auth/url")
    public ResponseDTO<List<RequestUrl>> getAuthUrl() {
        return menuService.getAuthUrl();
    }
}
