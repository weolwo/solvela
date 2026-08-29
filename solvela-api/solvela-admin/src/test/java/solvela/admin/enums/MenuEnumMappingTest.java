package solvela.admin.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.admin.module.system.menu.constant.MenuPermsTypeEnum;
import solvela.admin.module.system.menu.constant.MenuTypeEnum;
import solvela.admin.module.system.menu.dao.MenuDao;
import solvela.admin.module.system.menu.domain.vo.MenuVO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单表枚举化之后的真实验收（连数据库，只读）。
 *
 * <h3>为什么单独一个类</h3>
 * {@code t_menu} 在登录关键路径上：登录要拉整棵菜单树（{@code /menu/query} 平均 47KB），
 * 还要靠 {@code perms_type} 是否为 null 来决定一个菜单的 apiPerms 参不参与鉴权
 * （见 {@code LoginManager#getUserPermission}）。这条链断了不是少一列数据，是<b>登不进后台</b>。
 *
 * <p>另外这里有一处别的表没有的形态：{@code menu_type} 是用
 * {@code <foreach>} 拼 {@code IN (...)} 的，集合元素也要逐个走枚举 TypeHandler；
 * 而 {@code perms_type} 有 17 行 NULL，可空枚举列必须能装配成 null 而不是抛异常。
 *
 * <p>本测试<b>只读</b>：不造数、不改库。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class MenuEnumMappingTest {

    @Autowired
    private MenuDao menuDao;

    @Test
    @DisplayName("菜单树查询：menuType 能装配，permsType 允许为 null")
    void 菜单装配() {
        List<MenuVO> all = menuDao.queryMenuList(Boolean.FALSE, null, List.of());
        assertFalse(all.isEmpty(), "t_menu 没有数据，这条用例失去意义");

        boolean sawNullPerms = false;
        boolean sawPerms = false;
        for (MenuVO vo : all) {
            assertNotNull(vo.getMenuType(), "menuType 装配成了 null —— int 列没能落进枚举属性");
            if (vo.getPermsType() == null) {
                sawNullPerms = true;
            } else {
                assertEquals(MenuPermsTypeEnum.SA_TOKEN, vo.getPermsType());
                sawPerms = true;
            }
        }
        assertTrue(sawPerms, "一条 permsType 非空的都没有，可空列的装配没被真正验证到");
        assertTrue(sawNullPerms, "一条 permsType 为 null 的都没有，null 分支没被验证到");
    }

    @Test
    @DisplayName("<foreach> 拼 IN (...)：集合里的枚举也要按 value 下推")
    void 按类型集合过滤() {
        List<MenuVO> all = menuDao.queryMenuList(Boolean.FALSE, null, List.of());
        List<MenuVO> onlyMenu = menuDao.queryMenuList(Boolean.FALSE, null,
                List.of(MenuTypeEnum.CATALOG, MenuTypeEnum.MENU));

        assertFalse(onlyMenu.isEmpty(), "按 目录+菜单 过滤一条都没查到 —— foreach 里的枚举多半被写成了枚举名");
        assertTrue(onlyMenu.size() < all.size(), "过滤后数量没有变少，IN 条件根本没生效");

        for (MenuVO vo : onlyMenu) {
            assertTrue(vo.getMenuType() == MenuTypeEnum.CATALOG || vo.getMenuType() == MenuTypeEnum.MENU,
                    "过滤结果里混进了 " + vo.getMenuType());
        }

        long expected = all.stream()
                .filter(v -> v.getMenuType() == MenuTypeEnum.CATALOG || v.getMenuType() == MenuTypeEnum.MENU)
                .count();
        assertEquals(expected, onlyMenu.size(), "SQL 过滤的结果与内存过滤对不上");
    }

    @Test
    @DisplayName("鉴权口径不变：permsType 非空且 apiPerms 非空的菜单才贡献权限点")
    void 权限点口径() {
        List<MenuVO> all = menuDao.queryMenuList(Boolean.FALSE, null, List.of());
        long contributing = all.stream()
                .filter(v -> v.getPermsType() != null)
                .filter(v -> v.getApiPerms() != null && !v.getApiPerms().isEmpty())
                .count();
        assertTrue(contributing > 0,
                "没有任何菜单能贡献权限点 —— 登录后会是一个没有任何权限的账号");
    }
}
