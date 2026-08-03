-- ============================================================
-- v3.52.0  首页营销大屏：统计接口的权限功能点
--
-- 背景：首页从「假图表 + 公众号卡片」改造成营销作战大屏，数据来自 /marketingStat 下的
--       七个只读接口（participation / overview / prizeHealth / gameplay /
--       taskFunnel / eventHealth / topMembers）。
--
-- 🔴 为什么必须建这个功能点：
--    Controller 上挂了 @SaCheckPermission("marketingStat:query")，而 t_menu 里若没有
--    对应的 api_perms，**这个权限就没法授予任何角色** —— 除超管外所有人打开首页都会
--    收到「没有权限」，且界面上没有任何线索。本项目已因此栽过两次：
--    prizeLog:approve 与 proposalRecord:approve 在库里连功能点都不存在，
--    导致审批工作台对受限角色彻底不可用（见交接文档 §4.6④）。
--
-- ⚠️ 七个接口刻意共用一个权限串：统计接口全是只读的，拆成七个功能点只会让运营
--    在授权界面上勾七次，而没有任何多出来的控制力。
--
-- ⚠️ 原先代码里写的是 marketingStat:participation（首页参与图那版），
--    但那个串在库里从来没有对应功能点，等于从未被授予过 ——
--    所以这次统一成 marketingStat:query 不需要迁移任何存量授权。
--
-- ⚠️ menu_id 取 459/460：执行前已核对库里 MAX(menu_id) = 458。
--    换环境执行前先跑：SELECT MAX(menu_id) FROM t_menu;
--
-- 可重复执行：先按 menu_id 删再插。
-- ============================================================

DELETE FROM `t_role_menu` WHERE `menu_id` IN (459, 460);
DELETE FROM `t_menu` WHERE `menu_id` IN (459, 460);

INSERT INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
                      `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
                      `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`,
                      `create_user_id`)
VALUES
    -- 挂在 370「活动中心」下作为权限归属点。
    -- ⚠️ visible_flag = 0：它不是侧边栏菜单 —— 大屏就是首页本身（前端静态路由 /home，
    --    由 router/system/home.js 提供），侧边栏再出现一条「营销大屏」会让人以为是另一个页面。
    --    visible_flag 只影响侧边栏显示，角色授权树里照常可见可勾选。
    (459, '首页·营销大屏', 2, 370, 9, '/home',
     '/system/home/index.vue', 1, NULL, NULL, 'DashboardOutlined', NULL,
     0, NULL, 0, 0, 0, 0, 1),

    -- 功能点：与 MarketingStatController 上的 @SaCheckPermission 一一对应（七个接口共用一串）
    (460, '统计查询', 3, 459, NULL, NULL, NULL, 1, 'marketingStat:query', 'marketingStat:query', NULL, 459,
     0, NULL, 0, 1, 0, 0, 1);

-- 给超级管理员角色授权。
-- 超管（administrator_flag = 1）本就绕过全部权限校验，这行是给「角色是 ADMIN 但不是超管」
-- 的账号用的 —— 本项目的权限问题恰恰只在这类账号上才暴露。
--
-- ⚠️ 实测：开发库里 t_role 的 5 个角色**没有一个 role_code = 'ADMIN'**（多数 role_code 为 NULL），
--    所以这段在开发库里更新 0 行，与 v3.42.0.sql 当初的情况一致，不是脚本写错。
--    验收「受限角色能不能用大屏」时，必须先手工给某个角色勾上 460，再用该角色的账号测。
INSERT INTO `t_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM `t_role` r
         CROSS JOIN (SELECT 459 AS menu_id UNION ALL SELECT 460) m
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `t_role_menu` rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 自查：
-- SELECT menu_id, menu_name, menu_type, parent_id, path, api_perms, visible_flag
--   FROM t_menu WHERE menu_id IN (459, 460);
-- SELECT COUNT(*) FROM t_role_menu WHERE menu_id IN (459, 460);
--
-- ⚠️ 授权后不会立刻生效：@SaCheckPermission 走的是 @Cacheable(USER_PERMISSION) 的
--    LoginManager.getUserPermission()，而 RoleMenuService.updateRoleMenu 不清这个缓存
--    （基座缺陷，见交接文档 §4.5）。**改完权限必须登出重登**，否则菜单出来了功能还是被拒。
