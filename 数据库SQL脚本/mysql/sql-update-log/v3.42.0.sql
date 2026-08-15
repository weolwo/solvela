-- ⚠️ 必须保留这一行，且必须在所有语句之前。
-- 缺了它，mysql 客户端会用默认连接字符集（本项目 Docker 环境里是 latin1）解释本文件的 UTF-8 中文，
-- 逐字节转存进 utf8mb4 列 —— 中文全部变成乱码；中文列注释较长的建表语句还会撞上列注释
-- 1024 字符上限直接失败（v3.47.0 曾因此中断整批升级，排查成本远高于这四行）。
SET NAMES utf8mb4;

-- 活动创建向导菜单与权限功能点（2026-07-29，活动创建向导 P2）
--
-- 挂在 370「活动中心」下，与 372「活动配置」同级，排在它前面（sort=-1）——
-- 向导是主入口，活动配置列表是管理视图，运营日常先用前者。
--
-- ⚠️ path 与 component 是两套口径，别混：
--    path      = 前端路由地址，router.push 用的就是它（活动配置是 /activity/activity-config/list）
--    component = src/views 下的相对路径（活动配置是 /business/activity/activity-config/xxx.vue）
--    两者写错都不会报错，只会在点开时白屏 —— v3.39 清掉的号码池菜单就是这么来的。
--
-- ⚠️ 本脚本执行前已核对：库里最大 menu_id = 440（v3.41 占用 438~440），441 起安全。
--    若在别的环境执行，先跑一次：SELECT MAX(menu_id) FROM t_menu;
--
-- 可重复执行：先按 menu_id 删再插。

DELETE FROM `t_role_menu` WHERE `menu_id` IN (441, 442, 443, 444, 445);
DELETE FROM `t_menu` WHERE `menu_id` IN (441, 442, 443, 444, 445);

INSERT INTO `t_menu` (`menu_id`, `menu_name`, `menu_type`, `parent_id`, `sort`, `path`, `component`,
                      `perms_type`, `api_perms`, `web_perms`, `icon`, `context_menu_id`,
                      `frame_flag`, `frame_url`, `cache_flag`, `visible_flag`, `disabled_flag`, `deleted_flag`,
                      `create_user_id`)
VALUES
    (441, '活动创建向导', 2, 370, -1, '/activity/activity-wizard',
     '/business/activity/activity-wizard/ActivityWizard.vue', 1, NULL, NULL, 'RocketOutlined', NULL,
     0, NULL, 0, 1, 0, 0, 1),

    -- 功能点：与 ActivityConfigController 上的 @SaCheckPermission 一一对应。
    -- P0 之前这些串是 ":addProposal"（提案域的动作名被整段复制过来）且缺前缀，
    -- 与任何功能点都对不上；开发期管理员有全量权限，测不出来，只有受限角色才暴露。
    (442, '查询', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:query', 'activityConfig:query', NULL, 441,
     0, NULL, 0, 1, 0, 0, 1),
    (443, '新建活动', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:add', 'activityConfig:add', NULL, 441,
     0, NULL, 0, 1, 0, 0, 1),
    (444, '编辑活动/升级玩法', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:update', 'activityConfig:update', NULL, 441,
     0, NULL, 0, 1, 0, 0, 1),
    (445, '删除活动', 3, 441, NULL, NULL, NULL, 1, 'activityConfig:delete', 'activityConfig:delete', NULL, 441,
     0, NULL, 0, 1, 0, 0, 1);

-- 给超级管理员角色授权，否则新菜单建了也看不见。
-- 只对已存在的角色补授权，不新建角色。
INSERT INTO `t_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM `t_role` r
         CROSS JOIN (SELECT 441 AS menu_id UNION ALL SELECT 442 UNION ALL SELECT 443
                     UNION ALL SELECT 444 UNION ALL SELECT 445) m
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `t_role_menu` rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 自查：
-- SELECT menu_id, menu_name, parent_id, sort, path, component FROM t_menu WHERE menu_id BETWEEN 441 AND 445;
-- SELECT menu_id, menu_name, sort FROM t_menu WHERE parent_id = 370 ORDER BY sort, menu_id;
-- 若登录后仍看不到菜单：确认账号所属角色已被授权（t_role_menu），或该账号是 administrator（走全量菜单）。
